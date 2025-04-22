package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandler;
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

            String[] directories = getRdfDirectories();

            for (String directoryPath : directories) {
                processDirectory(directoryPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RDF model", e);
        }
    }

    private String[] getRdfDirectories() {
        return new String[] {
            "/home/efaundez/imgpedia_backend/rdfs",
            // "/NAS/sferrada/imgpedia/resource/img",
            // "/NAS/sferrada/imgpedia/resource/sim",
            "/NAS/sferrada/imgpedia/resource/wiki", 
            // "/NAS/sferrada/imgpedia/resource/dbp",
        };
    }

    private void processDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".ttl") || name.endsWith(".rdf"));
            if (files != null) {
                for (File file : files) {
                    processFile(file);
                }
            }
        } else {
            System.err.println("Directory not valid: " + directoryPath);
        }
    }

    private void processFile(File file) {
        try(InputStream inputStream = new FileInputStream(file)){
            System.out.println("Loading file: " + file.getAbsolutePath());
            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.filenameToLang(file.getName()))
                    .errorHandler(createErrorHandler())
                    .parse(model.getGraph());
        } catch (Exception e) {
            System.err.println("Error while loading file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private ErrorHandler createErrorHandler() {
        return new ErrorHandler() {
            @Override
            public void warning(String message, long line, long col) {
            }

            @Override
            public void error(String message, long line, long col) {
            }

            @Override
            public void fatal(String message, long line, long col) {
            }
        };
    }

    @Bean(name = "rdfModel")
    @ApplicationScope
    public Model rdfModel() {
        return this.model;
    }
}
