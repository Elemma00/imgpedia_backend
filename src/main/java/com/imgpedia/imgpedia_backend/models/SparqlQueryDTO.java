package com.imgpedia.imgpedia_backend.models;

import java.text.Format;
import java.util.Optional;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SparqlQueryDTO {

    @NotBlank(message = "SPARQL query cannot be empty")
    private String query;
    
    private Optional<String> graph = Optional.empty();
    
    @Min(value = 0, message = "Timeout must be greater than or equal to 0")
    private Integer timeout = 0;
    
    @Pattern(
        regexp = "^(json|xml|csv|tsv)$", 
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "must be a string and be one of these options: JSON, XML, CSV"
    )
    private String format = "json";

   
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
    public Optional<String> getGraph() {
        return graph;
    }
    public void setGraph(String graph) {
        this.graph = Optional.ofNullable(graph);
    }
    public Integer getTimeout() {
        return timeout;
    }
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
    
    public String getFormat() {
        return format;
    }
    public void setFormat(String format) {
        this.format = format;
    }

}
