package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Configuration class for RDF data loading and processing.
 * This class initializes
 * the RDF model, processes RDF files from specified directories, and handles compressed files.
 * It also manages the dataset connection and provides beans for the RDF model and dataset.
 */

@Configuration
public class RdfConfiguration {

    private static final String DB = System.getenv("TDB_PATH") != null ? 
    System.getenv("TDB_PATH") : 
    System.getProperty("user.dir") + File.separator + "imgpedia_tdb";
    private static final String TRACKER_FILE = DB + File.separator + "loaded_files.properties";

    private final Dataset dataset;
    private final Model model;
    private final RdfLoadTracker loadTracker;

    /**
     * Constructor for RdfConfiguration.
     * Initializes the dataset and model, and sets up the load tracker.
     */
    public RdfConfiguration() {
        this.dataset = TDB2Factory.connectDataset(DB);
        dataset.begin(ReadWrite.READ);
        ImgpediaLogger.info("Loading RDF model from TDB directory: " + DB);
        this.model = dataset.getDefaultModel();
        dataset.end();
        this.loadTracker = new RdfLoadTracker(TRACKER_FILE);
    }

    /**
     * Initializes the RDF model by loading data from specified directories.
     * It processes files in batches and handles compressed files.
     */
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
            
            for (String directoryPath : directories) {
                try {
                    processDirectory(directoryPath, 100); // Batch of 100 files
                    ImgpediaLogger.info("Completed processing directory: " + directoryPath);
                } catch (Exception e) {
                    ImgpediaLogger.error("Error processing directory " + directoryPath + ": " + e.getMessage());
                }
            }
            
            ImgpediaLogger.info("RDF data loading completed");
        } catch (Exception e) {
            ImgpediaLogger.error("Critical error during RDF initialization: " + e.getMessage());
            throw new RuntimeException("Error during RDF initialization", e);
        } finally {
            dataset.close();
        }
    }

    /**
     * Returns an array of directory paths to process RDF files.
     * @return Array of directory paths
     */
    private String[] getRdfDirectories() {
        return new String[] {
            "/nas_mount/imgpedia/resource/sim", 
            "/nas_mount/imgpedia/resource/img",
            "/nas_mount/imgpedia/resource/wiki", 
            "/home/efaundez/sanitized",
        };
    }

    /**
     * Processes RDF files in the specified directory.
     * It handles both compressed and uncompressed files, and manages memory usage.
     * @param directoryPath Path to the directory containing RDF files
     * @param batchSize Size of the batch for processing files
     */
    private void processDirectory(String directoryPath, int batchSize) {
    File directory = new File(directoryPath);
    if (!directory.exists() || !directory.isDirectory()) {
        ImgpediaLogger.error("Directory not valid: " + directoryPath);
        return;
    }
    
    File[] files = directory.listFiles((dir, name) -> 
        name.endsWith(".ttl") || name.endsWith(".rdf") || name.endsWith(".tar.gz"));
    
    if (files == null || files.length == 0) {
        ImgpediaLogger.info("No applicable files found in directory: " + directoryPath);
        return;
    }
    
    ImgpediaLogger.info("Found " + files.length + " files to process in " + directoryPath);

    List<File> successfulBatch = new ArrayList<>();
    int totalProcessed = 0;
    int totalSuccess = 0;
    
    dataset.begin(ReadWrite.WRITE);
    ImgpediaLogger.info("Started transaction for batch processing");
    
    for (File file : files) {
        if (loadTracker.isFileLoaded(file)) {
            ImgpediaLogger.info("Skipping already loaded file: " + file.getName());
            totalProcessed++;
            continue;
        }
        
        if (checkAvailableMemory()) {
            ImgpediaLogger.warn("Low memory detected, performing garbage collection");
            System.gc();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        boolean success = false;
        
        try {
            if (file.getName().endsWith(".tar.gz")) {
                success = processCompressedFile(file);
            } else {
                success = processFile(file);
            }
            
            if (success) {
                successfulBatch.add(file);
                ImgpediaLogger.info("Successfully processed file: " + file.getName() + " (pending commit)");
            } else {
                ImgpediaLogger.error("Failed to process file: " + file.getName());
            }
        } catch (OutOfMemoryError e) {
            ImgpediaLogger.error("Out of memory while processing " + file.getName() + ". Skipping.");
            
            if (!successfulBatch.isEmpty()) {
                commitBatchAndMarkAsLoaded(successfulBatch);
                totalSuccess += successfulBatch.size();
                successfulBatch.clear();
                
                dataset.begin(ReadWrite.WRITE);
            }
            
            System.gc();
            try {
                Thread.sleep(5000); 
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            ImgpediaLogger.error("Error processing file " + file.getName() + ": " + e.getMessage());
        }
        
        totalProcessed++;
        
        if (successfulBatch.size() >= batchSize) {
            ImgpediaLogger.info("Reached batch size of " + batchSize + " files, committing...");
            commitBatchAndMarkAsLoaded(successfulBatch);
            totalSuccess += successfulBatch.size();
            successfulBatch.clear();
            dataset.begin(ReadWrite.WRITE);
        }
    }
    
    if (!successfulBatch.isEmpty()) {
        ImgpediaLogger.info("Processing final batch of " + successfulBatch.size() + " files");
        commitBatchAndMarkAsLoaded(successfulBatch);
        totalSuccess += successfulBatch.size();
    } else if (dataset.isInTransaction()) {
        dataset.end();
    }
    
    ImgpediaLogger.info("Directory processing complete: " + directoryPath + 
        " - Successfully processed " + totalSuccess + "/" + totalProcessed + 
        " files out of " + files.length + " total files");
    }   

    /**
     * Commits the batch of files to the dataset and marks them as loaded.
     * @param batch List of files to commit
     */
    private void commitBatchAndMarkAsLoaded(List<File> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            ImgpediaLogger.info("Committing batch of " + batch.size() + " files...");
            dataset.commit();
            
            // Only mark files as loaded after successful commit
            for (File file : batch) {
                loadTracker.markFileAsLoaded(file);
                ImgpediaLogger.info("Marked file as loaded after commit: " + file.getName());
            }
            
            ImgpediaLogger.info("Batch committed successfully: " + batch.size() + " files");
        } catch (Exception e) {
            ImgpediaLogger.error("Error during batch commit: " + e.getMessage());
            if (dataset.isInTransaction()) {
                dataset.abort();
            }
        } finally {
            if (dataset.isInTransaction()) {
                dataset.end();
            }
            
           // Help garbage collection
            System.gc();
        }
    }

    private boolean processFile(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            ImgpediaLogger.info("Loading file: " + file.getAbsolutePath());

            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.filenameToLang(file.getName()))
                    .errorHandler(createErrorHandler())
                    .parse(new EncodeIRI(model));
            
            ImgpediaLogger.info("Successfully loaded file content: " + file.getName() + " (pending commit)");
            return true;
        } catch (Exception e) {
            ImgpediaLogger.error("Error loading file: " + file.getAbsolutePath() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Processes a compressed file (tar.gz) and extracts its contents.
     * It handles each entry in the compressed file and processes them individually.
     * @param compressedFile Path to the compressed file
     * @return true if successful, false otherwise
     */
    private boolean processCompressedFile(File compressedFile) {
        ImgpediaLogger.info("Processing compressed file: " + compressedFile.getAbsolutePath());
        int entriesProcessed = 0;
        int entriesSuccessful = 0;
        
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
                    return false;
                }
                
                boolean extractedSuccessfully = false;
                
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
                    extractedSuccessfully = true;
                } catch (Exception e) {
                    ImgpediaLogger.error("Failed to extract entry to temp file: " + e.getMessage());
                }
                
                entriesProcessed++;
                
                if (extractedSuccessfully) {
                    try {
                        boolean entrySuccess = processTemporaryFile(tempFile, entryName);
                        if (entrySuccess) {
                            entriesSuccessful++;
                        }
                    } catch (Exception e) {
                        ImgpediaLogger.error("Error processing temp file: " + e.getMessage());
                    }
                }

                if (!tempFile.delete()) {
                    ImgpediaLogger.error("Failed to delete temp file: " + tempFile.getAbsolutePath());
                    tempFile.deleteOnExit();
                }
        
                if (entriesProcessed % 5 == 0) {
                    System.gc();
                }
            }

        } catch (Exception e) {
            ImgpediaLogger.error("Error processing compressed file: " + compressedFile.getAbsolutePath() + " - " + e.getMessage());
            e.printStackTrace();
        }

        ImgpediaLogger.info("Processed " + entriesSuccessful + "/" + entriesProcessed + 
            " entries from compressed file: " + compressedFile.getName());
            
        return entriesSuccessful > 0;
    }

    private boolean processTemporaryFile(File tempFile, String originalName) {
        try (InputStream inputStream = new FileInputStream(tempFile)) {
            ImgpediaLogger.info("Loading entry from temp file: " + originalName);
        
            Model tempModel = ModelFactory.createDefaultModel();
            
            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.TURTLE)
                    .errorHandler(createErrorHandler())
                    .parse(new EncodeIRI(tempModel));
                    
            // Larger batch size for better performance
            int batchSize = 50000;
            int statementsAdded = 0;
            long totalStatements = tempModel.size();
            
            StmtIterator stmtIter = tempModel.listStatements();
            while (stmtIter.hasNext()) {
                for (int i = 0; i < batchSize && stmtIter.hasNext(); i++) {
                    model.add(stmtIter.next());
                    statementsAdded++;
                }
        
                if (statementsAdded % 500000 == 0) {
                    ImgpediaLogger.info("Added " + statementsAdded + "/" + totalStatements + 
                        " statements from " + originalName);
                    // Allow other operations to proceed
                    Thread.yield();
                }
            }
            
            ImgpediaLogger.info("Successfully loaded entry: " + originalName + " (" + statementsAdded + " statements) (pending commit)");
            
            // Help garbage collection
            tempModel = null;
            return true;
        } catch (Exception e) {
            ImgpediaLogger.error("Error loading entry from temp file: " + originalName + " - " + e.getMessage());
            return false;
        }
    }
    /**
     * Checks if the available memory is below a certain threshold.
     * @return true if memory usage exceeds the threshold, false otherwise
     */
    private boolean checkAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsageRatio = (double) usedMemory / maxMemory;
        
        return memoryUsageRatio > 0.8; // 80% threshold
    }

    /**
     * Creates an error handler for RDF parsing.
     * @return ErrorHandler instance
     * 
     * OBS: This should have a logger to log errors
     * and warnings, but for now it does nothing.
     * This is intendent to avoid flooding the logs with errors
     */
    public ErrorHandler createErrorHandler() {
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

    /**
     * Closes the dataset connection and releases resources.
     * This method is called when the application context is destroyed.
     */
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

    @Bean(name = "rdfDataset")
    @ApplicationScope
    public Dataset rdfDataset() {
        return this.dataset;
    }
    
}
