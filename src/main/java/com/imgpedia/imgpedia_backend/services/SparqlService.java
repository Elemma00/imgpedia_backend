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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.imgpedia.imgpedia_backend.models.SparqlQueryDTO;

@Service
public class SparqlService {

    @Autowired()
    @Qualifier("rdfModel")
    private Model rdfModel;

    private final AtomicReference<QueryExecution> currentQueryExecution = new AtomicReference<>();

    public ResultSet executeQuery(SparqlQueryDTO queryDTO) throws InterruptedException, ExecutionException {
        String queryString = queryDTO.getQuery();
        String graph = queryDTO.getGraph().orElse(null);
        Integer timeout = queryDTO.getTimeout();

        Query query = QueryFactory.create(queryString, Syntax.syntaxSPARQL_11_sim);
    
        try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
            currentQueryExecution.set(qexec);
            if (graph != null && !graph.isEmpty()) {
                System.out.println("GRAPH: " + graph);
            }
            if (timeout == 0 || timeout == null) {
                ResultSet originalResults = qexec.execSelect();
                return ResultSetFactory.copyResults(originalResults);
            
            }else {
                CompletableFuture<ResultSet> future = CompletableFuture.supplyAsync(() -> {
                    ResultSet originalResults = qexec.execSelect();
                    return ResultSetFactory.copyResults(originalResults);
                });
    
                try {
                    ResultSet results = future.orTimeout(timeout, TimeUnit.MILLISECONDS).get();
                    return results;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TimeoutException) {
                        qexec.abort();
                        throw new RuntimeException("La consulta excedió el tiempo límite de " + timeout + "ms");
                    } else {
                        throw e;
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
