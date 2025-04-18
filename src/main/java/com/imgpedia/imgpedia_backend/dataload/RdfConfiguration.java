package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb1.TDB1Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

import jakarta.annotation.PostConstruct;

@Configuration
public class RdfConfiguration {

    private static final String DB = System.getProperty("user.dir") + File.separator + "imgpedia_tdb";

    private final Dataset dataset = TDB1Factory.createDataset(DB);

    private final Model model = dataset.getDefaultModel();

    @PostConstruct
    public void initRdfModel() {
        try {

            if (!model.isEmpty()) {
                return;
            }
    
            // Directorios que contienen los archivos RDF
            String[] directories = {
                "/home/efaundez/imgpedia_backend/rdfs",
                // "/NAS/sferrada/imgpedia/resource/img",
                // "/NAS/sferrada/imgpedia/resource/sim",
                // "/NAS/sferrada/imgpedia/resource/wiki"
            };

            // Iterar sobre los directorios y cargar todos los archivos .ttl y .rdf
            for (String directoryPath : directories) {
                File directory = new File(directoryPath);
                if (directory.exists() && directory.isDirectory()) {
                    File[] files = directory.listFiles((dir, name) -> name.endsWith(".ttl") || name.endsWith(".rdf"));
                    if (files != null) {
                        for (File file : files) {
                            System.out.println("Cargando archivo: " + file.getAbsolutePath());
                            RDFDataMgr.read(model, file.getAbsolutePath());
                        }
                    }
                } else {
                    System.err.println("El directorio no existe o no es válido: " + directoryPath);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RDF model", e);
        }
    }

    @Bean(name = "rdfModel")
    @ApplicationScope
    public Model rdfModel() {
        return this.model;
    }
}