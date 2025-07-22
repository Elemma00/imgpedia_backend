package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.tdb1.TDB1Factory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Configuration class for RDF data loading and TDB dataset management.
 * Handles initialization, conversion, validation, and cleanup of RDF files.
 */
@Configuration
public class RdfConfiguration {

    private static final String DB_PATH = System.getenv("TDB_PATH") != null
            ? System.getenv("TDB_PATH")
            : System.getProperty("user.dir") + File.separator + "imgpedia_tdb";
    private static final String TRACKER_FILE = DB_PATH + File.separator + "loaded_files.properties";
    private static final String EXPORTS_DIR = DB_PATH + File.separator + "exports";
    private static final int THREAD_POOL_SIZE = 22;

    private Model model;
    private Dataset dataset;
    private RdfLoadTracker loadTracker;

    public RdfConfiguration() {}

    /**
     * Initializes the RDF model and dataset.
     * Loads or creates the TDB dataset and prepares export directories.
     */
    @PostConstruct
    public void initRdfModel() {
        ImgpediaLogger.info("Initializing RDF model...");
        this.loadTracker = new RdfLoadTracker(TRACKER_FILE);

        createDirectoryIfNotExists(DB_PATH);
        createDirectoryIfNotExists(EXPORTS_DIR);

        List<File> rdfFiles = collectRdfFiles(getRdfDirectories());
        ImgpediaLogger.info("Total IMGpedia files: " + rdfFiles.size());

        File tdbDir = new File(DB_PATH + File.separator + "tdb");
        if (tdbDir.exists()) {
            ImgpediaLogger.info("Found TDB, Skipping processing.");
            this.dataset = TDB1Factory.createDataset(tdbDir.getAbsolutePath());
            this.model = dataset.getDefaultModel();
            return;
        }

        // Uncomment to enable conversion and validation steps
        // String sanitizedDir = EXPORTS_DIR + File.separator + "sanitized";
        // convertTtlToNTriplesWithCleaning(getRdfDirectories(), EXPORTS_DIR, sanitizedDir);
        // validateAllNtFilesWithXLoader(EXPORTS_DIR, sanitizedDir);
        runTdb1XLoader();

        this.dataset = TDB1Factory.createDataset(tdbDir.getAbsolutePath());
        this.model = dataset.getDefaultModel();
    }

    /**
     * Returns the directories containing RDF files.
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
     * Collects all RDF files (.ttl, .rdf, .tar.gz) from the given directories.
     */
    private List<File> collectRdfFiles(String[] directories) {
        List<File> allFiles = new ArrayList<>();
        for (String directoryPath : directories) {
            File dir = new File(directoryPath);
            File[] files = dir.listFiles((d, name) ->
                name.endsWith(".ttl") || name.endsWith(".rdf") || name.endsWith(".tar.gz"));
            if (files != null) {
                Collections.addAll(allFiles, files);
            }
        }
        return allFiles;
    }

    /**
     * Creates a directory if it does not exist.
     */
    private void createDirectoryIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Prints progress information.
     */
    private void printProgress(int current, int total, String stage) {
        int percent = (int) ((current * 100.0f) / total);
        ImgpediaLogger.info("[" + stage + "] Progress: " + current + "/" + total + " (" + percent + "%)");
    }

    /**
     * Creates a silent error handler for RDF parsing.
     */
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

    /**
     * Closes the dataset and releases resources.
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

    /**
     * Converts .ttl files to N-Triples, cleans and validates them.
     */
    private void convertTtlToNTriplesWithCleaning(String[] directories, String exportDir, String sanitizedDir) {
        ImgpediaLogger.info("Converting .ttl files to N-Triples with cleaning and riot validation...");
        createDirectoryIfNotExists(exportDir);
        createDirectoryIfNotExists(sanitizedDir);

        List<File> ttlFiles = new ArrayList<>();
        for (String directoryPath : directories) {
            File dir = new File(directoryPath);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".ttl"));
            if (files != null) Collections.addAll(ttlFiles, files);
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        final int totalFiles = ttlFiles.size();
        final AtomicInteger processedCount = new AtomicInteger(0);

        for (File file : ttlFiles) {
            String ntFilePath = new File(exportDir, file.getName().replaceAll("\\.ttl$", ".nt")).getAbsolutePath();
            File ntFile = new File(ntFilePath);

            if (ntFile.exists() || loadTracker.isFileLoaded(file)) {
                ImgpediaLogger.info("Skipping already processed file: " + file.getName());
                printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                continue;
            }

            executor.submit(() -> processTtlFile(file, ntFilePath, sanitizedDir, processedCount, totalFiles));
        }
        shutdownExecutor(executor, "TTL->NT");
    }

    /**
     * Processes a single .ttl file: cleans, converts, validates, and logs results.
     */
    private void processTtlFile(File file, String ntFilePath, String sanitizedDir,
                                AtomicInteger processedCount, int totalFiles) {
        try {
            Model model = ModelFactory.createDefaultModel();
            boolean success = false;
            try (InputStream in = new FileInputStream(file)) {
                RDFParser.create()
                        .source(in)
                        .lang(Lang.TURTLE)
                        .errorHandler(createErrorHandler())
                        .parse(new EncodeIRI(model));
                success = true;
            } catch (Exception ex) {
                ImgpediaLogger.error("Error parsing TTL: " + file.getName() + " - " + ex.getMessage());
                reportBadFile(sanitizedDir, file.getName(), "Error parsing TTL: " + ex.getMessage());
                printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                return;
            }

            if (!success || model.isEmpty()) {
                ImgpediaLogger.error("No triples after cleaning: " + file.getName());
                reportBadFile(sanitizedDir, file.getName(), "No triples after cleaning");
                printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                return;
            }

            try (FileOutputStream out = new FileOutputStream(ntFilePath)) {
                model.write(out, "N-TRIPLES");
            }

            if (!validateWithRiot(ntFilePath)) {
                ImgpediaLogger.error("riot --validate failed: " + file.getName());
                reportBadFile(sanitizedDir, file.getName(), "riot --validate failed");
                new File(ntFilePath).delete();
                printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                return;
            }

            loadTracker.markFileAsLoaded(file);
            ImgpediaLogger.info("File cleaned and converted to N-Triples: " + ntFilePath);
            printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
        } catch (Exception e) {
            ImgpediaLogger.error("Error processing TTL file: " + e.getMessage());
            reportBadFile(sanitizedDir, file.getName(), "Exception: " + e.getMessage());
            printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
        }
    }

    /**
     * Validates an N-Triples file using riot.
     */
    private boolean validateWithRiot(String ntFilePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("riot", "--validate", ntFilePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            ImgpediaLogger.error("Error running riot validation: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reports a bad file by logging its name and error message.
     */
    private void reportBadFile(String sanitizedDir, String fileName, String errorMsg) {
        try {
            File reportFile = new File(sanitizedDir, "bad_ttl_files.txt");
            Files.write(
                reportFile.toPath(),
                (fileName + " :: " + errorMsg + System.lineSeparator()).getBytes(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ex) {
            ImgpediaLogger.error("Could not write error report for " + fileName + ": " + ex.getMessage());
        }
    }

    /**
     * Runs tdb1.xloader to load all valid .nt files into the TDB dataset.
     */
    public void runTdb1XLoader() {
        String tdbLoc = DB_PATH + File.separator + "tdb";
        String sanitizedDir = EXPORTS_DIR + File.separator + "sanitized";
        String logFile = DB_PATH + File.separator + "tdb1_xloader.log";

        File exportDirectory = new File(EXPORTS_DIR);
        File sanitizedDirectory = new File(sanitizedDir);

        List<File> ntFiles = new ArrayList<>();
        findFilesRecursiveExcludingSanitized(exportDirectory, ntFiles, sanitizedDirectory);

        if (ntFiles.isEmpty()) {
            ImgpediaLogger.error("No .nt files found in " + EXPORTS_DIR + " (excluding sanitized)");
            return;
        }

        List<String> command = new ArrayList<>();
        command.add("tdb1.xloader");
        command.add("--loc=" + tdbLoc);
        ntFiles.forEach(ntFile -> command.add(ntFile.getAbsolutePath()));

        ImgpediaLogger.info("Running tdb1.xloader with " + ntFiles.size() + " cleaned files");

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(logFile));
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                ImgpediaLogger.info("tdb1.xloader finished successfully. Log: " + logFile);
            } else {
                ImgpediaLogger.error("tdb1.xloader finished with errors. Check log: " + logFile);
            }
        } catch (Exception e) {
            ImgpediaLogger.error("Error running tdb1.xloader: " + e.getMessage());
        }
    }

    /**
     * Validates all .nt files using tdb1.xloader, moving corrupted files to sanitized.
     */
    public void validateAllNtFilesWithXLoader(String exportDir, String sanitizedDir) {
        ImgpediaLogger.info("Validating .nt files using tdb1.xloader in " + exportDir + " ...");
        File exportDirectory = new File(exportDir);
        File sanitizedDirectory = new File(sanitizedDir);
        createDirectoryIfNotExists(sanitizedDir);

        List<File> ntFiles = new ArrayList<>();
        findFilesRecursiveExcludingSanitized(exportDirectory, ntFiles, sanitizedDirectory);

        if (ntFiles.isEmpty()) {
            ImgpediaLogger.info("No .nt files to validate in " + exportDir + " (excluding sanitized)");
            return;
        }

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        final int totalFiles = ntFiles.size();
        final AtomicInteger processedCount = new AtomicInteger(0);
        final AtomicInteger corruptedCount = new AtomicInteger(0);
        final AtomicInteger okCount = new AtomicInteger(0);

        File errorLog = new File(sanitizedDirectory, "nt_xloader_validation_log.log");
        File validationDir = new File(sanitizedDirectory, "validation_temp");
        createDirectoryIfNotExists(validationDir.getAbsolutePath());

        ImgpediaLogger.info("Files to validate with tdb1.xloader: " + totalFiles);

        for (File ntFile : ntFiles) {
            executor.submit(() -> validateNtFile(ntFile, validationDir, sanitizedDirectory, errorLog,
                    processedCount, okCount, corruptedCount, totalFiles));
        }

        shutdownExecutor(executor, "nt xloader validation");

        // Cleanup validation directory
        deleteDirectory(validationDir);

        ImgpediaLogger.info("=== VALIDATION COMPLETED ===");
        ImgpediaLogger.info("Total OK: " + okCount.get());
        ImgpediaLogger.info("Total Corrupted: " + corruptedCount.get());
        ImgpediaLogger.info("Total Files: " + totalFiles);

        if (corruptedCount.get() > 0) {
            ImgpediaLogger.error("Found " + corruptedCount.get() + " corrupted files!");
            ImgpediaLogger.error("Files moved to: " + sanitizedDirectory.getAbsolutePath());
            ImgpediaLogger.error("See details in: " + errorLog.getAbsolutePath());
        }
    }

    /**
     * Validates a single .nt file using tdb1.xloader.
     */
    private void validateNtFile(File ntFile, File validationDir, File sanitizedDirectory, File errorLog,
                                AtomicInteger processedCount, AtomicInteger okCount, AtomicInteger corruptedCount, int totalFiles) {
        boolean isCorrupted = false;
        StringBuilder errorMsg = new StringBuilder();
        String tempTdbDir = null;
        String logFile = null;

        try {
            tempTdbDir = new File(validationDir, "tdb_" + Thread.currentThread().getId() + "_" + System.currentTimeMillis()).getAbsolutePath();
            logFile = new File(validationDir, "xloader_" + ntFile.getName() + "_" + System.currentTimeMillis() + ".log").getAbsolutePath();

            List<String> command = new ArrayList<>();
            command.add("tdb1.xloader");
            command.add("--loc=" + tempTdbDir);
            command.add(ntFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(logFile));

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                isCorrupted = true;
                try {
                    String logContent = new String(Files.readAllBytes(new File(logFile).toPath()));
                    errorMsg.append("tdb1.xloader validation failed (exit code: ")
                            .append(exitCode)
                            .append("): ")
                            .append(logContent.length() > 1000 ? logContent.substring(0, 1000) + "..." : logContent);
                } catch (Exception logEx) {
                    errorMsg.append("tdb1.xloader validation failed with exit code: ")
                            .append(exitCode)
                            .append(" (could not read log: ")
                            .append(logEx.getMessage())
                            .append(")");
                }
            } else {
                okCount.incrementAndGet();
                ImgpediaLogger.info("Validated successfully: " + ntFile.getName());
            }
        } catch (Exception ex) {
            isCorrupted = true;
            errorMsg.append("Validation exception: ").append(ex.getMessage());
        } finally {
            try {
                if (tempTdbDir != null) deleteDirectory(new File(tempTdbDir));
                if (logFile != null) new File(logFile).delete();
            } catch (Exception cleanupEx) {
                ImgpediaLogger.warn("Failed to cleanup temp directories for " + ntFile.getName() + ": " + cleanupEx.getMessage());
            }
        }

        if (isCorrupted) {
            corruptedCount.incrementAndGet();
            ImgpediaLogger.error("CORRUPTED FILE DETECTED: " + ntFile.getName() + " - Moving to sanitized...");
            moveCorruptedFile(ntFile, sanitizedDirectory, errorMsg, errorLog);
        }

        int done = processedCount.incrementAndGet();
        if (done % 10 == 0 || done == totalFiles) {
            ImgpediaLogger.info("[nt xloader Validation] Progress: " + done + "/" + totalFiles +
                    " | OK: " + okCount.get() +
                    " | Corrupted: " + corruptedCount.get());
        }
    }

    /**
     * Moves a corrupted file to the sanitized directory and logs the error.
     */
    private void moveCorruptedFile(File ntFile, File sanitizedDirectory, StringBuilder errorMsg, File errorLog) {
        File sanitizedNt = new File(sanitizedDirectory, ntFile.getName());
        boolean moveSuccess = false;
        String moveDetails = "";

        try {
            if (ntFile.exists()) {
                if (sanitizedNt.exists()) sanitizedNt.delete();
                moveSuccess = ntFile.renameTo(sanitizedNt);
                moveDetails = "renameTo: " + moveSuccess;
                if (!moveSuccess) {
                    Files.copy(ntFile.toPath(), sanitizedNt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    boolean deleteSuccess = ntFile.delete();
                    moveSuccess = deleteSuccess;
                    moveDetails = "copy+delete: delete=" + deleteSuccess;
                }
                if (moveSuccess) {
                    ImgpediaLogger.error("✓ MOVED CORRUPTED FILE TO SANITIZED: " + sanitizedNt.getAbsolutePath());
                } else {
                    ImgpediaLogger.error("✗ FAILED TO MOVE CORRUPTED FILE: " + ntFile.getAbsolutePath());
                }
            } else {
                ImgpediaLogger.warn("Original file no longer exists: " + ntFile.getAbsolutePath());
                moveDetails = "file_not_found";
            }
        } catch (Exception moveEx) {
            ImgpediaLogger.error("ERROR MOVING CORRUPTED FILE " + ntFile.getName() + " to sanitized: " + moveEx.getMessage());
            moveDetails = "exception: " + moveEx.getMessage();
            errorMsg.append(" | Move error: ").append(moveEx.getMessage());
        }

        synchronized (RdfConfiguration.class) {
            try {
                String logEntry = "[CORRUPTED] " + ntFile.getAbsolutePath() +
                        " :: " + errorMsg.toString() +
                        " | Move details: " + moveDetails +
                        " | Moved successfully: " + moveSuccess +
                        System.lineSeparator();
                Files.write(
                        errorLog.toPath(),
                        logEntry.getBytes(),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
                );
            } catch (Exception logEx) {
                ImgpediaLogger.error("Could not write validation log for " + ntFile.getName() + ": " + logEx.getMessage());
            }
        }
    }

    /**
     * Recursively finds .nt files, excluding the sanitized directory.
     */
    private void findFilesRecursiveExcludingSanitized(File directory, List<File> resultFiles, File sanitizedDirectory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!file.getAbsolutePath().equals(sanitizedDirectory.getAbsolutePath())) {
                        findFilesRecursiveExcludingSanitized(file, resultFiles, sanitizedDirectory);
                    }
                } else if (file.getName().endsWith(".nt")) {
                    resultFiles.add(file);
                }
            }
        }
    }

    /**
     * Deletes a directory and its contents recursively.
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * Shuts down an executor service and waits for termination.
     */
    private void shutdownExecutor(ExecutorService executor, String stage) {
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            ImgpediaLogger.error("Thread interrupted during " + stage + ": " + e.getMessage());
            Thread.currentThread().interrupt();
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