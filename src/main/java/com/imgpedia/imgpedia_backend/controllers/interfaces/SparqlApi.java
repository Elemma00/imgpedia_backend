package com.imgpedia.imgpedia_backend.controllers.interfaces;

import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * SparqlApi is an interface that defines the API for executing SPARQL queries.
 * It contains methods for executing a SPARQL query and stopping a running query.
 */
@Tag(name = "SPARQL API", description = "API for executing SPARQL queries")
public interface SparqlApi {
    
    /**
     * Executes a SPARQL query.
     * @param queryDTO The SPARQL query to be executed.
     * @param bindingResult The result of the validation of the queryDTO.
     * @return A ResponseEntity containing the result of the query execution.
     */
    @Operation(summary = "Execute a SPARQL query")
    @PostMapping("/query")
    ResponseEntity<?> query(@Valid @RequestBody SparqlQueryDTO queryDTO, BindingResult bindingResult);

    /**
     * Stops the currently running SPARQL query.
     * @return A ResponseEntity indicating the result of the stop operation.
     */
    @Operation(summary = "Stop the currently running SPARQL query")
    @PostMapping("/query/stop")
    ResponseEntity<?> stopQuery(@Valid @RequestBody Map<String, String> body);
}
