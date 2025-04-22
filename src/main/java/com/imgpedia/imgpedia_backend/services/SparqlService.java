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
import com.imgpedia.imgpedia_backend.utils.ErrorMessages;

@Service
public class SparqlService {

    private final Model rdfModel;
    
    public SparqlService(@Qualifier("rdfModel") Model rdfModel) {
        this.rdfModel = rdfModel;
    }

    private final AtomicReference<QueryExecution> currentQueryExecution = new AtomicReference<>();

    public ResultSet executeQuery(SparqlQueryDTO queryDTO) throws InterruptedException, ExecutionException, TimeoutException {
        String queryString = queryDTO.getQuery();
        String graph = queryDTO.getGraph().orElse(null);
        Integer timeout = queryDTO.getTimeout();
        Query query;
        
        try {
            ImgpediaLogger.logInfo("Creating query");
            query = QueryFactory.create(queryString, Syntax.syntaxSPARQL_11_sim);
        } catch (Exception e) {
            throw new MalformedQueryException(ErrorMessages.INVALID_QUERY_SYNTAX + ": " + e.getMessage());
        }
    
        try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
            ImgpediaLogger.logInfo("Executing query");
            currentQueryExecution.set(qexec);
            
            if (timeout == 0 || timeout == null) {
                ImgpediaLogger.logInfo("Query executed without timeout");
                ResultSet originalResults = qexec.execSelect();
                return ResultSetFactory.copyResults(originalResults);
            } else {
                ImgpediaLogger.logInfo("Query executed with timeout: " + timeout + "ms" );
                CompletableFuture<ResultSet> future = CompletableFuture.supplyAsync(() -> {
                    ResultSet originalResults = qexec.execSelect();
                    return ResultSetFactory.copyResults(originalResults);
                });
    
                try {
                    ResultSet results = future.orTimeout(timeout, TimeUnit.MILLISECONDS).get();
                    ImgpediaLogger.logInfo("Query executed successfully");
                    return results;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TimeoutException) {
                        qexec.abort();
                        throw new TimeoutException(ErrorMessages.QUERY_TIMEOUT + timeout + "ms");
                    } else {
                        throw new ExecutionException(ErrorMessages.QUERY_EXECUTION_FAILED, e);
                    }
                }
            }
        } finally {
            currentQueryExecution.set(null);
        }
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
