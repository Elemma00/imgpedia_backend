package com.imgpedia.imgpedia_backend.controllers;

import java.io.ByteArrayOutputStream;

import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.imgpedia.imgpedia_backend.services.SparqlService;

@RestController
@RequestMapping("api/sparql")
public class SparqlController {

    @Autowired
    private SparqlService sparqlService;

    @PostMapping("/query")
    public ResponseEntity<String> query(@RequestBody String sparqlQuery) {
        try {
            ResultSet results = sparqlService.executeQuery(sparqlQuery);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ResultSetFormatter.outputAsJSON(outputStream, results);
            return ResponseEntity.ok(outputStream.toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Query error: " + e.getMessage());
        }
    }
}
