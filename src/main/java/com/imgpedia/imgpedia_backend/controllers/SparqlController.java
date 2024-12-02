package com.imgpedia.imgpedia_backend.controllers;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;
import com.imgpedia.imgpedia_backend.services.SparqlService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/sparql")
public class SparqlController {

    @Autowired
    private SparqlService sparqlService;

    @PostMapping("/query")
    public ResponseEntity<?> query(@Valid @RequestBody SparqlQueryDTO queryDTO, BindingResult bindingResult) {

        if (bindingResult.hasFieldErrors()) {
            return validation(bindingResult);
        }

        try {
            ResultSet results = sparqlService.executeQuery(queryDTO);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            format_setter(queryDTO, results, outputStream);

            return ResponseEntity.ok(outputStream.toString());

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Query error: " + e.getMessage());
        }
    }

    private void format_setter(SparqlQueryDTO queryDTO, ResultSet results, ByteArrayOutputStream outputStream) {
        switch (queryDTO.getFormat().toLowerCase()) {
            case "json" -> ResultSetFormatter.outputAsJSON(outputStream, results);
            case "xml" -> ResultSetFormatter.outputAsXML(outputStream, results);
            case "csv" -> ResultSetFormatter.outputAsCSV(outputStream, results);
            case "tsv" -> ResultSetFormatter.outputAsTSV(outputStream, results);
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
