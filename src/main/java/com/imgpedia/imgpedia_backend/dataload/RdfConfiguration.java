package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.tdb1.TDB1Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class RdfConfiguration {

    private static final String DB = System.getenv("TDB_PATH") != null ? 
    System.getenv("TDB_PATH") : 
    System.getProperty("user.dir") + File.separator + "imgpedia_tdb";
    private final Dataset dataset;
    private final Model model;

    public RdfConfiguration() {
        this.dataset = TDB1Factory.createDataset(DB);
        this.model = dataset.getDefaultModel();
    }

    @PostConstruct
    public void initRdfModel() {

        File tdbDir = new File(DB);
        if (tdbDir.exists() && tdbDir.isDirectory() && tdbDir.list().length > 0) {
            System.out.println("TDB directory exists and contains files, checking model...");
        }
        
        try {
            dataset.begin(ReadWrite.READ);
            try {
                if (!model.isEmpty()) {
                    System.out.println("Dataset already contains data, skipping initialization");
                    return;
                }
            } finally {
                dataset.end();
            }

            String[] directories = getRdfDirectories();
            
            dataset.begin(ReadWrite.WRITE);
            try {
                for (String directoryPath : directories) {
                    processDirectory(directoryPath);
                }
                dataset.commit();
                System.out.println("RDF data loaded successfully");
            } catch (Exception e) {
                dataset.abort();
                throw new RuntimeException("Failed to initialize RDF model", e);
            } finally {
                dataset.end();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during RDF initialization", e);
        }
    }


    private String[] getRdfDirectories() {
        return new String[] {
            "/home/efaundez/sanitized",
            "/nas_mount/imgpedia/resource/wiki", 
            "/nas_mount/imgpedia/resource/img",
            "/nas_mount",
        };
    }
    private void processDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".ttl") || name.endsWith(".rdf") || name.endsWith(".tar.gz"));
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".tar.gz")) {
                        processCompressedFile(file);
                    } else {
                        processFile(file);
                    }
                }
            }
        } else {
            System.err.println("Directory not valid: " + directoryPath);
        }
    }

    private void processFile(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            System.out.println("Loading file: " + file.getAbsolutePath());

            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.filenameToLang(file.getName()))
                    .errorHandler(createErrorHandler())
                    .parse(new EncodeIRI(model));

        } catch (Exception e) {
            System.err.println("Error while loading file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private void processCompressedFile(File compressedFile) {
        try (FileInputStream fileInputStream = new FileInputStream(compressedFile);
             GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(fileInputStream);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gzipInputStream)) {
            
            System.out.println("Loading compressed file: " + compressedFile.getAbsolutePath());
            
            TarArchiveEntry currentEntry;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (!currentEntry.isDirectory() && currentEntry.getName().endsWith(".ttl")) {
                    System.out.println("Processing entry: " + currentEntry.getName());
                    
                    RDFParser.create()
                        .source(tarInput)
                        .lang(RDFLanguages.TURTLE)
                        .errorHandler(createErrorHandler())
                        .parse(new EncodeIRI(model));
                }
            }
        } catch (Exception e) {
            System.err.println("Error while loading compressed file: " + compressedFile.getAbsolutePath() + " - " + e.getMessage());
            e.printStackTrace();
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

    @PreDestroy
    public void closeDataset() {
        if (dataset != null) {
            try {
                if (dataset.isInTransaction()) {
                    dataset.abort();
                    dataset.end();
                }
              
                dataset.close();
                System.out.println("Dataset closed succesfully.");
            } catch (Exception e) {
                System.err.println("Error while closing dataset: " + e.getMessage());
            }
        }
    }

    @Bean(name = "rdfModel")
    @ApplicationScope
    public Model rdfModel() {
        return this.model;
    }
    
}
