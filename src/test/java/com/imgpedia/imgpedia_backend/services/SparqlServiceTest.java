package com.imgpedia.imgpedia_backend.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.imgpedia.imgpedia_backend.exceptions.MalformedQueryException;
import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;

class SparqlServiceTest {

    private Model rdfModel;

    @Mock
    private QueryExecution queryExecution;

    @Mock
    private ResultSet resultSet;

    @Mock
    private Query query;

    @InjectMocks
    private SparqlService sparqlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rdfModel = ModelFactory.createDefaultModel();
        sparqlService = new SparqlService(rdfModel);
    }

    @Test
    void testExecuteQueryWithoutTimeout() throws Exception {
        SparqlQueryDTO queryDTO = new SparqlQueryDTO();
        queryDTO.setQuery("SELECT * WHERE {?s ?p ?o}");
        ResultSet results = sparqlService.executeQuery(queryDTO);

        assertNotNull(results);
    }

    @Test
    void testExecuteQueryWithTimeout() throws Exception {
        SparqlQueryDTO queryDTO = new SparqlQueryDTO();
        queryDTO.setQuery("SELECT * WHERE {?s ?p ?o}");
        queryDTO.setTimeout(1000); // Timeout 1 second

        ResultSet results = sparqlService.executeQuery(queryDTO);

        assertNotNull(results);
    }

    @Test
    void testMalformedQueryException() {
        SparqlQueryDTO queryDTO = new SparqlQueryDTO();
        queryDTO.setQuery("MALFORMED QUERY");

        Exception exception = assertThrows(MalformedQueryException.class, () -> {
            sparqlService.executeQuery(queryDTO);
        });

        assertTrue(exception.getMessage().contains("The query syntax is invalid."));
    }

}