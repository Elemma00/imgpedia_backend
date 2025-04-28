package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.tdb1.TDB1Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class RdfConfiguration {

    private static final String DB = System.getenv("TDB_PATH") != null ? 
    System.getenv("TDB_PATH") : 
    System.getProperty("user.dir") + File.separator + "imgpedia_tdb";
    private final Dataset dataset;
    private final Model model;

    private static final String TRACKER_FILE = DB + File.separator + "loaded_files.properties";
    private final RdfLoadTracker loadTracker;

    public RdfConfiguration() {
        this.dataset = TDB1Factory.createDataset(DB);
        dataset.begin(ReadWrite.READ);
        ImgpediaLogger.info("Loading RDF model from TDB directory: " + DB);
        this.model = dataset.getDefaultModel();
        dataset.end();
        this.loadTracker = new RdfLoadTracker(TRACKER_FILE);
    }

    @PostConstruct
    public void initRdfModel() {
        ImgpediaLogger.info("Initializing RDF model...");
        File tdbDir = new File(DB);
        if (tdbDir.exists() && tdbDir.isDirectory() && tdbDir.list().length > 0) {
            ImgpediaLogger.info("TDB directory exists and contains files, checking model...");
        }
        
        try {

            dataset.begin(ReadWrite.READ);
            if(model.isEmpty()){
                ImgpediaLogger.warn("Default model is empty");
            }
            dataset.end();


            String[] directories = getRdfDirectories();
            
            try {
                for (String directoryPath : directories) {
                    processDirectory(directoryPath);
                }
                ImgpediaLogger.info("RDF data loaded successfully");
            } catch (Exception e) {
                if (dataset.isInTransaction()) {
                    dataset.abort(); 
                }
            } finally {
                if (dataset.isInTransaction()) {
                    dataset.end(); 
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during RDF initialization", e);
        }
    }


    private String[] getRdfDirectories() {
        return new String[] {
            "/nas_mount/imgpedia/resource/wiki", 
            "/nas_mount/imgpedia/resource/sim", 
            // "/nas_mount",
            "/home/efaundez/sanitized",
            "/nas_mount/imgpedia/resource/img",
        };
    }
    private void processDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".ttl") || name.endsWith(".rdf") || name.endsWith(".tar.gz"));
            if (files != null) {
                for (File file : files) {
                    dataset.begin(ReadWrite.WRITE);
                    if (file.getName().endsWith(".tar.gz")) {
                        processCompressedFile(file);
                    } else {
                        processFile(file);
                    }
                    dataset.commit();
                    dataset.end();
                }
            }
        } else {
            ImgpediaLogger.error("Directory not valid: " + directoryPath);
        }
    }

    private void processFile(File file) {
        if (loadTracker.isFileLoaded(file)) {
            return;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            ImgpediaLogger.info("Loading file: " + file.getAbsolutePath());

            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.filenameToLang(file.getName()))
                    .errorHandler(createErrorHandler())
                    .parse(new EncodeIRI(model));
            
            loadTracker.markFileAsLoaded(file);
            ImgpediaLogger.info("Successfully loaded");
          
        } catch (Exception e) {
            ImgpediaLogger.error("Error loading file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private void processCompressedFile(File compressedFile) {
        if (loadTracker.isFileLoaded(compressedFile)) {
            ImgpediaLogger.info("Skipping already loaded compressed file: " + compressedFile.getAbsolutePath());
            return;
        }
        ImgpediaLogger.info("Processing compressed file: " + compressedFile.getAbsolutePath());
        boolean overallSuccess = true;
        
        File tempDir = new File("/imgpedia/temp_extraction");
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (!created) {
                ImgpediaLogger.error("Failed to create temp directory: " + tempDir.getAbsolutePath());
                ImgpediaLogger.info("Attempting to use system temp directory instead");
                tempDir = new File(System.getProperty("java.io.tmpdir"));
            } else {
                ImgpediaLogger.info("Created temp directory: " + tempDir.getAbsolutePath());
            }
        }
        
        ImgpediaLogger.info("Using temp directory: " + tempDir.getAbsolutePath());
    
        try (FileInputStream fileInputStream = new FileInputStream(compressedFile);
             GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(fileInputStream);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gzipInputStream)) {
            
            TarArchiveEntry currentEntry;
            
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.isDirectory() || !currentEntry.getName().endsWith(".ttl")) {
                    continue;
                }
    
                String entryName = currentEntry.getName();
                ImgpediaLogger.info("Processing tar entry: " + entryName);
              
                String sanitizedName = entryName.replaceAll("[^a-zA-Z0-9.-]", "_");
                File tempFile = new File(tempDir, "tarentry_" + sanitizedName);
                
                ImgpediaLogger.info("Creating temp file: " + tempFile.getAbsolutePath());
                
                if (!tempDir.canWrite()) {
                    ImgpediaLogger.error("No write permission for directory: " + tempDir.getAbsolutePath());
                    overallSuccess = false;
                    continue;
                }
                
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192]; 
                    int bytesRead;
                    long totalBytes = 0;
                    
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
              
                        if (bytesRead < buffer.length) {
                            break;
                        }
                    }
                    
                    ImgpediaLogger.info("Wrote " + totalBytes + " bytes to temp file: " + tempFile.getAbsolutePath());
                
                }
                
     
                boolean entrySuccess = processTemporaryFile(tempFile, entryName);
                if (!entrySuccess) {
                    overallSuccess = false;
                    ImgpediaLogger.error("Failed to process entry: " + entryName);
                }
                
          
                if (!tempFile.delete()) {
                    ImgpediaLogger.error("Failed to delete temp file: " + tempFile.getAbsolutePath());
                }
          
                System.gc();
            }
    
        } catch (Exception e) {
            overallSuccess = false;
            ImgpediaLogger.error("Error processing compressed file: " + compressedFile.getAbsolutePath() + " - " + e.getMessage());
            e.printStackTrace();
        }
    
        if (overallSuccess) {
            loadTracker.markFileAsLoaded(compressedFile);
            ImgpediaLogger.info("Successfully processed compressed file: " + compressedFile.getAbsolutePath());
        } else {
            ImgpediaLogger.error("Failed to process compressed file: " + compressedFile.getAbsolutePath());
        }
    }

    private boolean processTemporaryFile(File tempFile, String originalName) {
        dataset.begin(ReadWrite.WRITE);
        boolean success = false;
        
        try (InputStream inputStream = new FileInputStream(tempFile)) {
            ImgpediaLogger.info("Loading entry from temp file: " + originalName);
        
            Model tempModel = ModelFactory.createDefaultModel();
            
            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.TURTLE)
                    .errorHandler(createErrorHandler())
                    .parse(tempModel);
                    
            int batchSize = 10000;
            int statementsAdded = 0;
            
            StmtIterator stmtIter = tempModel.listStatements();
            while (stmtIter.hasNext()) {
                for (int i = 0; i < batchSize && stmtIter.hasNext(); i++) {
                    model.add(stmtIter.next());
                    statementsAdded++;
                }
                
                if (statementsAdded % 100000 == 0) {
                    ImgpediaLogger.info("Added " + statementsAdded + " statements from " + originalName);
                }
            }
            
            dataset.commit();
            success = true;
            ImgpediaLogger.info("Successfully loaded entry: " + originalName + " (" + statementsAdded + " statements)");
        } catch (Exception e) {
            ImgpediaLogger.error("Error loading entry from temp file: " + originalName + " - " + e.getMessage());
            if (dataset.isInTransaction()) {
                dataset.abort();
            }
        } finally {
            if (dataset.isInTransaction()) {
                dataset.end();
            }
        }
        
        return success;
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
                ImgpediaLogger.info("Dataset closed successfully.");
            } catch (Exception e) {
                ImgpediaLogger.error("Error while closing dataset: " + e.getMessage());
            }
        }
    }

    @Bean(name = "rdfModel")
    @ApplicationScope
    public Model rdfModel() {
        return this.model;
    }
    
}
