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

@RestController
@RequestMapping("api/sparql")
public class SparqlController implements SparqlApi {

    @Autowired
    private SparqlService sparqlService;

    @Override
    public ResponseEntity<?> query(@Valid @RequestBody SparqlQueryDTO queryDTO, BindingResult bindingResult) {
        ImgpediaLogger.logRequest("POST", "/api/sparql/query", null, queryDTO.getQuery());

        if (bindingResult.hasFieldErrors())
            return validation(bindingResult);

        try {
            ResultSet results = sparqlService.executeQuery(queryDTO);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            format_setter(queryDTO, results, outputStream);

            ImgpediaLogger.logResponse(200, MessagesLogs.QUERY_EXECUTED_SUCCESS);

            return ResponseEntity.ok(outputStream.toString());

        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.QUERY_DEFAULT_ERROR + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> stopQuery() {
        ImgpediaLogger.logRequest("POST", "/api/sparql/query/stop", null, null);
        try {
            sparqlService.stopQuery();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Query stopped successfully");
            ImgpediaLogger.logResponse(200, MessagesLogs.QUERY_STOPPED);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", MessagesLogs.QUERY_STOP_ERROR + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private void format_setter(SparqlQueryDTO queryDTO, ResultSet results, ByteArrayOutputStream outputStream) {
        switch (queryDTO.getFormat().toLowerCase()) {
            case "json" -> ResultSetFormatter.outputAsJSON(outputStream, results);
            case "xml" -> ResultSetFormatter.outputAsXML(outputStream, results);
            case "csv" -> ResultSetFormatter.outputAsCSV(outputStream, results);
            case "tsv" -> ResultSetFormatter.outputAsTSV(outputStream, results);    
            case "ttl" -> ResultSetFormatter.output(outputStream, results, ResultsFormat.FMT_RDF_TTL);
            case "nt" -> ResultSetFormatter.output(outputStream, results, ResultsFormat.FMT_RDF_NT);
            default -> ResultSetFormatter.outputAsJSON(outputStream, results);
        }
    }

 
    private ResponseEntity<?> validation(BindingResult result) {
        Map<String, String> errors = new HashMap<>();

        result.getFieldErrors().forEach(error -> {
            errors.put(error.getField(), "The Field " + error.getField() + " " + error.getDefaultMessage());
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
