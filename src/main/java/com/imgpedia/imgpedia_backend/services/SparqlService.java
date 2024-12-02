package com.imgpedia.imgpedia_backend.services;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SparqlService {

    @Autowired
    private Model rdfModel;

    public ResultSet executeQuery(String queryString) {
        Query query = QueryFactory.create(queryString, Syntax.syntaxSPARQL_11_sim);
    
        try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
            ResultSet originalResults = qexec.execSelect();
          
            ResultSet results = ResultSetFactory.copyResults(originalResults);
            
            // ResultSetFormatter.out(System.out, results, query);
      
            return results;
        }
    }

    public boolean executeAsk(String queryString) {
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
            return qexec.execAsk();
        }
    }

    public Model executeConstruct(String queryString) {
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, rdfModel)) {
            return qexec.execConstruct();
        }
    }
}
