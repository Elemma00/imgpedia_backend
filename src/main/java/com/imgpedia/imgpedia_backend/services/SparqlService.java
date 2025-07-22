package com.imgpedia.imgpedia_backend.services;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
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
 * Provides methods to execute SELECT queries with optional timeouts and cancellation support.
 */
@Service
public class SparqlService {

    private final Model rdfModel;
    private final Dataset rdfDataset;

    /**
     * Stores active queries mapped by clientQueryId.
     * Each entry contains the QueryExecution and its associated CompletableFuture.
     */
    public final ConcurrentHashMap<String, Map.Entry<QueryExecution, CompletableFuture<ResultSet>>> activeQueries = new ConcurrentHashMap<>();

    /**
     * Constructor for SparqlService.
     *
     * @param rdfModel   The RDF model to query.
     * @param rdfDataset The RDF dataset for transactional operations.
     */
    public SparqlService(@Qualifier("rdfModel") Model rdfModel, @Qualifier("rdfDataset") Dataset rdfDataset) {
        this.rdfModel = rdfModel;
        this.rdfDataset = rdfDataset;
    }

    /**
     * Executes a SPARQL SELECT query asynchronously with optional timeout.
     *
     * @param queryDTO The DTO containing the query, timeout, and clientQueryId.
     * @return The ResultSet of the query.
     * @throws InterruptedException   If the execution is interrupted.
     * @throws ExecutionException    If the execution fails.
     * @throws TimeoutException      If the execution times out.
     */
    public ResultSet executeQuery(SparqlQueryDTO queryDTO)
            throws InterruptedException, ExecutionException, TimeoutException {

        String clientQueryId = queryDTO.getClientQueryId();
        validateClientQueryId(clientQueryId);

        Query query = createQuery(queryDTO.getQuery());
        Integer timeout = queryDTO.getTimeout();

        QueryExecution queryExecution = QueryExecutionFactory.create(query, rdfModel);

        CompletableFuture<ResultSet> future = CompletableFuture.supplyAsync(() -> executeSelect(queryExecution));

        activeQueries.put(clientQueryId, new AbstractMap.SimpleEntry<>(queryExecution, future));

        try {
            return getResultSetWithTimeout(future, timeout);
        } catch (CancellationException e) {
            throw new RuntimeException("Query cancelled by user");
        } finally {
            cleanupQuery(clientQueryId, queryExecution);
        }
    }

    /**
     * Validates that the clientQueryId is not null or empty.
     *
     * @param clientQueryId The client query identifier.
     */
    private void validateClientQueryId(String clientQueryId) {
        if (clientQueryId == null || clientQueryId.isEmpty()) {
            throw new IllegalArgumentException("clientQueryId is required");
        }
    }

    /**
     * Executes the SELECT query within a read transaction and returns a copy of the results.
     *
     * @param queryExecution The QueryExecution object.
     * @return The copied ResultSet.
     */
    private ResultSet executeSelect(QueryExecution queryExecution) {
        rdfDataset.begin(ReadWrite.READ);
        try {
            return copyResults(queryExecution.execSelect());
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                queryExecution.abort();
                throw new RuntimeException("Query cancelled by user");
            }
            throw new RuntimeException(e);
        } finally {
            rdfDataset.end();
        }
    }

    /**
     * Retrieves the ResultSet from the future, applying the timeout if specified.
     *
     * @param future  The CompletableFuture for the ResultSet.
     * @param timeout The timeout in milliseconds, or null/0 for no timeout.
     * @return The ResultSet.
     * @throws InterruptedException If interrupted.
     * @throws ExecutionException  If execution fails.
     * @throws TimeoutException    If timeout occurs.
     */
    private ResultSet getResultSetWithTimeout(CompletableFuture<ResultSet> future, Integer timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (timeout == null || timeout == 0) {
            return future.get();
        }
        return future.get(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Cleans up the query from the activeQueries map and closes the QueryExecution.
     *
     * @param clientQueryId   The client query identifier.
     * @param queryExecution  The QueryExecution to close.
     */
    private void cleanupQuery(String clientQueryId, QueryExecution queryExecution) {
        activeQueries.remove(clientQueryId);
        queryExecution.close();
    }

    /**
     * Creates a SPARQL Query object from a query string.
     *
     * @param queryString The SPARQL query string.
     * @return The Query object.
     * @throws MalformedQueryException If the query syntax is invalid.
     */
    private Query createQuery(String queryString) {
        try {
            ImgpediaLogger.info("Creating query");
            return QueryFactory.create(queryString, Syntax.syntaxSPARQL_11_sim);
        } catch (Exception e) {
            throw new MalformedQueryException(MessagesLogs.INVALID_QUERY_SYNTAX + ": " + e.getMessage());
        }
    }

    /**
     * Copies the results from the original ResultSet.
     *
     * @param originalResults The original ResultSet.
     * @return A copy of the ResultSet.
     */
    private ResultSet copyResults(ResultSet originalResults) {
        return ResultSetFactory.copyResults(originalResults);
    }

    /**
     * Stops an active query by its clientQueryId.
     * Aborts the QueryExecution and cancels the associated future.
     *
     * @param clientQueryId The client query identifier.
     */
    public void stopQuery(String clientQueryId) {
        Map.Entry<QueryExecution, CompletableFuture<ResultSet>> entry = activeQueries.get(clientQueryId);
        if (entry != null) {
            entry.getKey().abort();
            entry.getValue().cancel(true);
            activeQueries.remove(clientQueryId);
        }
    }
}
