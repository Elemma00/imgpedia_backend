package com.imgpedia.imgpedia_backend.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.jena.query.ResultSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;
import com.imgpedia.imgpedia_backend.services.SparqlService;



class SparqlControllerTest {

    @InjectMocks
    private SparqlController sparqlController;

    @Mock
    private SparqlService sparqlService;

    @Mock
    private BindingResult bindingResult;

    @Mock
    private ResultSet resultSet;

    @Captor
    private ArgumentCaptor<SparqlQueryDTO> queryCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void query_ShouldReturnOk_WhenNoValidationErrors() throws InterruptedException, ExecutionException, TimeoutException {
        SparqlQueryDTO queryDTO = new SparqlQueryDTO();
        queryDTO.setQuery("SELECT * WHERE {?s ?p ?o}");
        queryDTO.setFormat("json");

        when(bindingResult.hasFieldErrors()).thenReturn(false);
        when(sparqlService.executeQuery(any())).thenReturn(resultSet);

        ResponseEntity<?> response = sparqlController.query(queryDTO, bindingResult);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertTrue(response.getBody() instanceof String);
        verify(sparqlService).executeQuery(queryCaptor.capture());
        assertEquals("SELECT * WHERE {?s ?p ?o}", queryCaptor.getValue().getQuery());
    }

    @Test
    void query_ShouldReturnBadRequest_WhenValidationErrors() {
        SparqlQueryDTO queryDTO = new SparqlQueryDTO();
        when(bindingResult.hasFieldErrors()).thenReturn(true);

        FieldError fieldError = new FieldError("object", "query", "must not be null");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<?> response = sparqlController.query(queryDTO, bindingResult);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> errors = (Map<?, ?>) response.getBody();
        assertTrue(errors.containsKey("query"));
    }

    @Test
    void query_ShouldReturnBadRequest_WhenCancelledByUser() throws InterruptedException, ExecutionException, TimeoutException {
        SparqlQueryDTO queryDTO = new SparqlQueryDTO();
        queryDTO.setQuery("SELECT * WHERE {?s ?p ?o}");
        queryDTO.setFormat("json");

        when(bindingResult.hasFieldErrors()).thenReturn(false);
        when(sparqlService.executeQuery(any()))
                .thenThrow(new RuntimeException("cancelled by user"));

        ResponseEntity<?> response = sparqlController.query(queryDTO, bindingResult);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("La consulta fue cancelada por el usuario.", body.get("error"));
    }

    @Test
    void stopQuery_ShouldReturnOk_WhenSuccess() {
        Map<String, String> body = new HashMap<>();
        body.put("clientQueryId", "abc123");

        doNothing().when(sparqlService).stopQuery("abc123");

        ResponseEntity<?> response = sparqlController.stopQuery(body);

        assertEquals(HttpStatusCode.valueOf(200) , response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> respBody = (Map<?, ?>) response.getBody();
        assertEquals("Query stopped successfully", respBody.get("message"));
    }

    @Test
    void stopQuery_ShouldReturnBadRequest_WhenException() {
        Map<String, String> body = new HashMap<>();
        body.put("clientQueryId", "abc123");

        doThrow(new RuntimeException("Stop error")).when(sparqlService).stopQuery("abc123");

        ResponseEntity<?> response = sparqlController.stopQuery(body);

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> respBody = (Map<?, ?>) response.getBody();
        assertTrue(((String) respBody.get("error")).contains("Stop error"));
    }
}