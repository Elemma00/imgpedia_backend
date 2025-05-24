package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
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

@Configuration
public class RdfConfiguration {

    private static final String DB = System.getenv("TDB_PATH") != null ?
            System.getenv("TDB_PATH") :
            System.getProperty("user.dir") + File.separator + "imgpedia_tdb";
    private static final String TRACKER_FILE = DB + File.separator + "loaded_files.properties";
    private static final String ERROR_REPORT_FILE = DB + File.separator + "errors_ttl.txt";
    private Model model;
    private Dataset dataset;
    private RdfLoadTracker loadTracker;

    public RdfConfiguration() {}

    @PostConstruct
    public void initRdfModel() {
        ImgpediaLogger.info("Initializing RDF model...");
        this.loadTracker = new RdfLoadTracker(TRACKER_FILE);

        File dbDir = new File(DB);
        if (!dbDir.exists()) dbDir.mkdirs();

        // Create export directory
        String exportDir = DB + File.separator + "exports";
        File exportDirectory = new File(exportDir);
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs();
        }

        // Get all files to process
        String[] directories = getRdfDirectories();
        List<File> allFiles = new ArrayList<>();
        for (String directoryPath : directories) {
            File dir = new File(directoryPath);
            File[] files = dir.listFiles((dir1, name) ->
            name.endsWith(".ttl") || name.endsWith(".rdf") || name.endsWith(".tar.gz"));
            if (files != null) {
            Collections.addAll(allFiles, files);
            }
        }
        int totalFiles = allFiles.size();
        ImgpediaLogger.info("Total files to process: " + totalFiles);

        final int NUM_THREADS = 20;
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Process files in parallel
        for (File file : allFiles) {
            executor.submit(() -> {
            // Skip already processed files
            if (loadTracker.isFileLoaded(file)) {
                ImgpediaLogger.info("Skipping already loaded file: " + file.getName());
                int current = processedCount.incrementAndGet();
                printProgress(current, totalFiles, "Processing files");
                return;
            }

            try {
                // Create a new model for this file
                Model fileModel = ModelFactory.createDefaultModel();
                boolean success = false;

                // Parse the file
                try (InputStream inputStream = new FileInputStream(file)) {
                ImgpediaLogger.info("Loading file: " + file.getAbsolutePath());
                RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.filenameToLang(file.getName()))
                    .errorHandler(createErrorHandler())
                    .parse(new EncodeIRI(fileModel));
                ImgpediaLogger.info("Successfully loaded file: " + file.getName() + " with " + fileModel.size() + " triples");
                success = true;
                } catch (Exception e) {
                ImgpediaLogger.error("Error loading file: " + file.getAbsolutePath() + " - " + e.getMessage());
                }

                // Write to output file if successful
                if (success) {
                    String outputFilename = exportDir + File.separator + file.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
                    try (FileOutputStream out = new FileOutputStream(outputFilename)) {
                        ImgpediaLogger.info("Writing " + fileModel.size() + " triples to " + outputFilename);
                        fileModel.write(out, "TTL");
                        ImgpediaLogger.info("Successfully wrote to: " + outputFilename);

                        // Validar con riot --validate
                        ProcessBuilder pb = new ProcessBuilder("riot", "--validate", outputFilename);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();
                        StringBuilder riotOutput = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                riotOutput.append(line).append(System.lineSeparator());
                            }
                        }
                        int exitCode = process.waitFor();

                        // Si hay ERROR, escribir en el archivo de errores
                        String riotOutStr = riotOutput.toString();
                        if (riotOutStr.contains("ERROR")) {
                            synchronized (RdfConfiguration.class) {
                                try (java.io.FileWriter fw = new java.io.FileWriter(ERROR_REPORT_FILE, true)) {
                                    fw.write("Archivo: " + outputFilename + System.lineSeparator());
                                    fw.write(riotOutStr + System.lineSeparator());
                                }
                            }
                            ImgpediaLogger.error("Archivo con ERROR en riot --validate: " + outputFilename);
                        } else {
                            ImgpediaLogger.info("Archivo validado sin errores: " + outputFilename);
                        }

                        // Mark file as loaded using synchronized access
                        synchronized (loadTracker) {
                            loadTracker.markFileAsLoaded(file);
                        }
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        ImgpediaLogger.error("Error writing TTL file: " + e.getMessage());
                    }
                }

                // Free memory
                fileModel.close();

                // Update progress
                int current = processedCount.incrementAndGet();
                printProgress(current, totalFiles, "Processing files");
                
            } catch (Exception e) {
                ImgpediaLogger.error("Error processing file: " + file.getName() + " - " + e.getMessage());
                int current = processedCount.incrementAndGet();
                printProgress(current, totalFiles, "Processing files");
            }
            });
        }

        // Wait for all tasks to complete
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            ImgpediaLogger.error("Thread execution was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        ImgpediaLogger.info("File processing completed. Successfully processed " + successCount.get() + 
                " out of " + totalFiles + " files. Output files are in: " + exportDir);
        
        // Create an empty dataset for the beans
        this.dataset = TDB2Factory.connectDataset(DB + File.separator + "tdb");
        this.model = dataset.getDefaultModel();
    }

    private void printProgress(int current, int total, String etapa) {
        int percent = (int) ((current * 100.0f) / total);
        ImgpediaLogger.info("[" + etapa + "] Progress: " + current + "/" + total + " (" + percent + "%)");
    }

    private String[] getRdfDirectories() {
        return new String[] {
                "/nas_mount/imgpedia/resource/sim",
                "/nas_mount/imgpedia/resource/img",
                "/nas_mount/imgpedia/resource/wiki",
                "/home/efaundez/sanitized",
        };
    }

    private void deleteDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted((a, b) -> b.compareTo(a)) // Borra hijos antes que padres
                        .forEach(p -> {
                            try { Files.delete(p); } catch (Exception ignored) {}
                        });
            }
        } catch (Exception e) {
            ImgpediaLogger.error("Error deleting directory: " + path + " - " + e.getMessage());
        }
    }

    public ErrorHandler createErrorHandler() {
        return new ErrorHandler() {
            @Override
            public void warning(String message, long line, long col) {}
            @Override
            public void error(String message, long line, long col) {}
            @Override
            public void fatal(String message, long line, long col) {}
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

    @Bean(name = "rdfDataset")
    @ApplicationScope
    public Dataset rdfDataset() {
        return this.dataset;
    }
}