package com.imgpedia.imgpedia_backend.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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

@Service
public class SparqlService {

    private final Model rdfModel;
    
    public SparqlService(@Qualifier("rdfModel") Model rdfModel) {
        this.rdfModel = rdfModel;
    }

    private final AtomicReference<QueryExecution> currentQueryExecution = new AtomicReference<>();
    public ResultSet executeQuery(SparqlQueryDTO queryDTO) throws InterruptedException, ExecutionException, TimeoutException {
        rdfModel.getGraph().getTransactionHandler().begin();
        Query query = createQuery(queryDTO.getQuery());
        Integer timeout = queryDTO.getTimeout();

        try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
            currentQueryExecution.set(qexec);
            return executeQueryWithTimeout(qexec, timeout);
        } finally {
            currentQueryExecution.set(null);
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

    private ResultSet executeQueryWithTimeout(QueryExecution qexec, Integer timeout) throws InterruptedException, ExecutionException, TimeoutException {
        if (timeout == null || timeout == 0) {
            ImgpediaLogger.info("Executing query without timeout");
            return copyResults(qexec.execSelect());
        } else {
            ImgpediaLogger.info("Executing query with timeout: " + timeout + "ms");
            return executeWithTimeout(qexec, timeout);
        }
    }

    private ResultSet executeWithTimeout(QueryExecution qexec, Integer timeout) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<ResultSet> future = CompletableFuture.supplyAsync(() -> copyResults(qexec.execSelect()));

        try {
            return future.orTimeout(timeout, TimeUnit.MILLISECONDS).get();
        } catch (ExecutionException e) {
            handleExecutionException(qexec, e, timeout);
            return null;
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

    public void stopQuery() {
        QueryExecution qexec = currentQueryExecution.getAndSet(null);
        if (qexec != null) {
            qexec.abort();
        }
    }

    // public boolean executeAsk(String queryString) {
    //     Query query = QueryFactory.create(queryString);
    //     try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
    //         return qexec.execAsk();
    //     }
    // }

    // public Model executeConstruct(String queryString) {
    //     Query query = QueryFactory.create(queryString);
    //     try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
    //         return qexec.execConstruct();
    //     }
    // }
}
