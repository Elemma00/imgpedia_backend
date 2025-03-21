package com.imgpedia.imgpedia_backend.controllers.interfaces;

import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "SPARQL API", description = "API for executing SPARQL queries")
public interface SparqlApiController {

    @Operation(summary = "Execute a SPARQL query")
    @PostMapping("/query")
    ResponseEntity<?> query(@Valid @RequestBody SparqlQueryDTO queryDTO, BindingResult bindingResult);

    @Operation(summary = "Stop the currently running SPARQL query")
    @PostMapping("/query/stop")
    ResponseEntity<?> stopQuery();
}
