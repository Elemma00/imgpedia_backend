package com.imgpedia.imgpedia_backend.services;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.imgpedia.imgpedia_backend.exceptions.MalformedQueryException;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;

/**
 * Service class for executing SPARQL queries on a Jena RDF model.
 * This class provides methods to execute SELECT queries with optional timeouts.
 */
@Service
public class SparqlService {

    private final Model rdfModel;
   
    private final ConcurrentHashMap<String, Map.Entry<QueryExecution, CompletableFuture<ResultSet>>> activeQueries = new ConcurrentHashMap<>();

    public SparqlService(@Qualifier("rdfModel") Model rdfModel) {
        this.rdfModel = rdfModel;
    }

   public ResultSet executeQuery(SparqlQueryDTO queryDTO) throws InterruptedException, ExecutionException, TimeoutException {
        String clientQueryId = queryDTO.getClientQueryId();
        if (clientQueryId == null || clientQueryId.isEmpty()) {
            throw new IllegalArgumentException("clientQueryId is required");
        }
        Query query = createQuery(queryDTO.getQuery());
        Integer timeout = queryDTO.getTimeout();

        QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel);
        CompletableFuture<ResultSet> future = CompletableFuture.supplyAsync(() -> {
        rdfModel.begin();
        try {
            return copyResults(qexec.execSelect());
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                qexec.abort();
                throw new RuntimeException("Query cancelled by user");
            }
            throw new RuntimeException(e);
        } finally {
            rdfModel.close();
        }
    });

        activeQueries.put(clientQueryId, new AbstractMap.SimpleEntry<>(qexec, future));
        try {
            if (timeout == null || timeout == 0) {
                return future.get();
            } else {
                return future.get(timeout, TimeUnit.MILLISECONDS);
            }
        } catch (CancellationException e) {
            throw new RuntimeException("Query cancelled by user");
        } finally {
            activeQueries.remove(clientQueryId);
            qexec.close();
        }
    }


    private Query createQuery(String queryString) {
        try {
            ImgpediaLogger.info("Creating query");
            return QueryFactory.create(queryString, Syntax.syntaxSPARQL_11_sim);
        } catch (Exception e) {
            throw new MalformedQueryException(MessagesLogs.INVALID_QUERY_SYNTAX + ": " + e.getMessage());
        }
    }


    private void handleExecutionException(QueryExecution qexec, ExecutionException e, Integer timeout) throws ExecutionException, TimeoutException {
        if (e.getCause() instanceof TimeoutException) {
            qexec.abort();
            throw new TimeoutException(MessagesLogs.QUERY_TIMEOUT + timeout + "ms");
        } else {
            throw new ExecutionException(MessagesLogs.QUERY_EXECUTION_FAILED, e);
        }
    }

    private ResultSet copyResults(ResultSet originalResults) {
        return ResultSetFactory.copyResults(originalResults);
    }

    public void stopQuery(String clientQueryId) {
        Map.Entry<QueryExecution, CompletableFuture<ResultSet>> entry = activeQueries.get(clientQueryId);
        if (entry != null) {
            entry.getKey().abort();
            entry.getValue().cancel(true);
            activeQueries.remove(clientQueryId);
        }
    }

}
