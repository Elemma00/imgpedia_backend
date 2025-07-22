package com.imgpedia.imgpedia_backend.controllers;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.controllers.interfaces.SparqlApi;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;
import com.imgpedia.imgpedia_backend.services.SparqlService;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;

import jakarta.validation.Valid;

/**
 * Controller for handling SPARQL query requests.
 */
@RestController
@RequestMapping("api/sparql")
public class SparqlController implements SparqlApi {

    private final SparqlService sparqlService;

    @Autowired
    public SparqlController(SparqlService sparqlService) {
        this.sparqlService = sparqlService;
    }

    /**
     * Handles SPARQL query execution.
     *
     * @param queryDTO      DTO containing the SPARQL query and format.
     * @param bindingResult Validation result.
     * @return ResponseEntity with query results or error.
     */
    @Override
    public ResponseEntity<?> query(@Valid @RequestBody SparqlQueryDTO queryDTO, BindingResult bindingResult) {
        ImgpediaLogger.logRequest("POST", "/api/sparql/query", null, queryDTO.getQuery());

        if (bindingResult.hasFieldErrors()) {
            return buildValidationErrorResponse(bindingResult);
        }

        try {
            ResultSet results = sparqlService.executeQuery(queryDTO);
            String formattedResults = formatResults(queryDTO, results);

            ImgpediaLogger.logResponse(HttpStatus.OK.value(), MessagesLogs.QUERY_EXECUTED_SUCCESS);
            return ResponseEntity.ok(formattedResults);

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("cancelled by user")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "La consulta fue cancelada por el usuario."));
            }
            throw e;
        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.QUERY_DEFAULT_ERROR + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Handles stopping a running SPARQL query.
     *
     * @param body Map containing the clientQueryId.
     * @return ResponseEntity with success or error message.
     */
    @Override
    public ResponseEntity<?> stopQuery(@Valid @RequestBody Map<String, String> body) {
        ImgpediaLogger.logRequest("POST", "/api/sparql/query/stop", null, null);
        try {
            String clientQueryId = body.get("clientQueryId");
            sparqlService.stopQuery(clientQueryId);

            Map<String, String> response = Map.of("message", "Query stopped successfully");
            ImgpediaLogger.logResponse(HttpStatus.OK.value(), MessagesLogs.QUERY_STOPPED);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> errorResponse = Map.of(
                    "error", MessagesLogs.QUERY_STOP_ERROR + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Formats the SPARQL ResultSet according to the requested format.
     *
     * @param queryDTO DTO containing the requested format.
     * @param results  SPARQL ResultSet.
     * @return String with formatted results.
     */
    private String formatResults(SparqlQueryDTO queryDTO, ResultSet results) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String format = queryDTO.getFormat() != null ? queryDTO.getFormat().toLowerCase() : "json";

        switch (format) {
            case "json" -> ResultSetFormatter.outputAsJSON(outputStream, results);
            case "xml" -> ResultSetFormatter.outputAsXML(outputStream, results);
            case "csv" -> ResultSetFormatter.outputAsCSV(outputStream, results);
            case "tsv" -> ResultSetFormatter.outputAsTSV(outputStream, results);
            case "ttl" -> ResultSetFormatter.output(outputStream, results, ResultsFormat.FMT_RDF_TTL);
            case "nt" -> ResultSetFormatter.output(outputStream, results, ResultsFormat.FMT_RDF_NT);
            default -> ResultSetFormatter.outputAsJSON(outputStream, results);
        }
        return outputStream.toString();
    }

    /**
     * Builds a validation error response.
     *
     * @param result BindingResult containing validation errors.
     * @return ResponseEntity with error details.
     */
    private ResponseEntity<?> buildValidationErrorResponse(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        result.getFieldErrors().forEach(error ->
                errors.put(error.getField(), "The Field " + error.getField() + " " + error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
