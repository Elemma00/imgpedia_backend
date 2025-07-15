package com.imgpedia.imgpedia_backend.services;

import java.util.concurrent.CompletableFuture;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.rdf.model.Model;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.imgpedia.imgpedia_backend.exceptions.MalformedQueryException;
import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;




class SparqlServiceTest {

    @Mock
    private Model mockModel;

    @Mock
    private Dataset mockDataset;

    @InjectMocks
    private SparqlService sparqlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sparqlService = new SparqlService(mockModel, mockDataset);
    }

    @Test
    void testExecuteQuery_successful() throws Exception {
        String queryStr = "SELECT * WHERE {?s ?p ?o}";
        String clientQueryId = "testId";
        SparqlQueryDTO dto = new SparqlQueryDTO();
        dto.setQuery(queryStr);
        dto.setClientQueryId(clientQueryId);
        dto.setTimeout(1000);

        QueryExecution mockQExec = mock(QueryExecution.class);
        ResultSet mockResultSet = mock(ResultSet.class);
        ResultSetRewindable copiedResultSet = ResultSetFactory.copyResults(mockResultSet);

        // Mock static methods
        try (MockedStatic<QueryExecutionFactory> qef = mockStatic(QueryExecutionFactory.class);
             MockedStatic<ResultSetFactory> rsf = mockStatic(ResultSetFactory.class)) {
            qef.when(() -> QueryExecutionFactory.create(any(Query.class), eq(mockModel))).thenReturn(mockQExec);
            when(mockQExec.execSelect()).thenReturn(mockResultSet);
            rsf.when(() -> ResultSetFactory.copyResults(mockResultSet)).thenReturn(copiedResultSet);

            // No need to stub begin() and close() for mockModel as they are void methods on a mock.

            ResultSet result = sparqlService.executeQuery(dto);
            assertFalse(result.hasNext());
        }
    }

    @Test
    void testExecuteQuery_nullClientQueryId_throws() {
        SparqlQueryDTO dto = new SparqlQueryDTO();
        dto.setQuery("SELECT * WHERE {?s ?p ?o}");
        dto.setClientQueryId(null);

        assertThrows(IllegalArgumentException.class, () -> sparqlService.executeQuery(dto));
    }

    @Test
    void testExecuteQuery_malformedQuery_throws() {
        SparqlQueryDTO dto = new SparqlQueryDTO();
        dto.setQuery("MALFORMED QUERY");
        dto.setClientQueryId("id");

        assertThrows(MalformedQueryException.class, () -> sparqlService.executeQuery(dto));
    }

    @Test
    void testStopQuery_removesAndCancels() throws Exception {
        String clientQueryId = "stopTest";
        QueryExecution mockQExec = mock(QueryExecution.class);
        CompletableFuture<ResultSet> mockFuture = mock(CompletableFuture.class);

        // Insert a fake running query
        sparqlService.activeQueries.put(clientQueryId, new java.util.AbstractMap.SimpleEntry<>(mockQExec, mockFuture));

        sparqlService.stopQuery(clientQueryId);

        verify(mockQExec).abort();
        verify(mockFuture).cancel(true);
        assertNull(sparqlService.activeQueries.get(clientQueryId));
    }
}