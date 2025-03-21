package com.imgpedia.imgpedia_backend.dataload;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
public class RdfConfiguration {

    @Bean(name = "rdfModel")
    @ApplicationScope
    public Model rdfModel() {
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, "./rdfs/vec3.rdf");
            // Load ontology
            RDFDataMgr.read(model, "./rdfs/imgpedia.ttl", Lang.TURTLE);
            // Load instances
            RDFDataMgr.read(model, "./rdfs/imgpedia_instances.ttl", Lang.TURTLE);
            // Load similarity
            // RDFDataMgr.read(model, "./rdfs/imgpedia_relations.ttl", Lang.TURTLE);

            return model;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RDF model", e);
        }
    }
}
