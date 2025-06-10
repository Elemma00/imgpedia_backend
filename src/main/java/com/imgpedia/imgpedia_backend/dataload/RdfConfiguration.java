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
        ImgpediaLogger.info("Total IMGpedia files: " + totalFiles);

        File tdbDir = new File(DB + File.separator + "tdb");
        if (tdbDir.exists()) {
            ImgpediaLogger.info("Found TDB, Skipping processing.");
            this.dataset = TDB1Factory.createDataset(DB + File.separator + "tdb");
            this.model = dataset.getDefaultModel();
            return;
        } else {
            String sanitizedDir = exportDir + File.separator + "sanitized";
            convertTtlToNTriplesWithCleaning(directories, exportDir, sanitizedDir);
            validateAllNtFilesWithXLoader(exportDir, sanitizedDir);
            runTdb1XLoader();
        }

        // Create an empty dataset for the beans
        this.dataset = TDB1Factory.createDataset(DB + File.separator + "tdb");
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

    private void convertTtlToNTriplesWithCleaning(String[] directories, String exportDir, String sanitizedDir) {
        ImgpediaLogger.info("Converting .ttl files to N-Triples with cleaning and riot validation...");
        File outDirectory = new File(exportDir);
        File sanitizedDirectory = new File(sanitizedDir);
        if (!outDirectory.exists()) outDirectory.mkdirs();
        if (!sanitizedDirectory.exists()) sanitizedDirectory.mkdirs();

        List<File> allFiles = new ArrayList<>();
        for (String directoryPath : directories) {
            File dir = new File(directoryPath);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".ttl"));
            if (files != null) Collections.addAll(allFiles, files);
        }

        final int THREADS = 22;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        final int totalFiles = allFiles.size();
        final AtomicInteger processedCount = new AtomicInteger(0);

        for (File file : allFiles) {
            // Salta si ya existe el .nt correspondiente en el exportDir
            String ntFilePath = new File(outDirectory, file.getName().replaceAll("\\.ttl$", ".nt")).getAbsolutePath();
            File ntFile = new File(ntFilePath);
            if (ntFile.exists()) {
                ImgpediaLogger.info("Skipping already converted file: " + file.getName());
                printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                continue;
            }

            synchronized (loadTracker) {
                if (loadTracker.isFileLoaded(file)) {
                    ImgpediaLogger.info("Skipping already loaded file: " + file.getName());
                    printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                    continue;
                }
            }
            executor.submit(() -> {
                try {
                    // 1. Limpia y parsea con EncodeIRI
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
                        reportBadFile(sanitizedDirectory, file.getName(), "Error parsing TTL: " + ex.getMessage());
                        printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                        return;
                    }

                    if (!success || model.isEmpty()) {
                        ImgpediaLogger.error("No triples after cleaning: " + file.getName());
                        reportBadFile(sanitizedDirectory, file.getName(), "No triples after cleaning");
                        printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                        return;
                    }

                    // 2. Escribe el modelo limpio a un archivo N-Triples
                    try (FileOutputStream out = new FileOutputStream(ntFilePath)) {
                        model.write(out, "N-TRIPLES");
                    }

                    // 3. Valida el archivo N-Triples usando riot --validate
                    ProcessBuilder pbRiot = new ProcessBuilder(
                        "riot", "--validate", ntFilePath
                    );
                    pbRiot.redirectErrorStream(true);
                    Process processRiot = pbRiot.start();
                    int exitCodeRiot = processRiot.waitFor();

                    boolean riotOk = (exitCodeRiot == 0);
                    if (!riotOk) {
                        ImgpediaLogger.error("riot --validate failed: " + file.getName());
                        reportBadFile(sanitizedDirectory, file.getName(), "riot --validate failed: exit code " + exitCodeRiot);
                        // Borra el .nt si se creó
                        File ntToDelete = new File(ntFilePath);
                        if (ntToDelete.exists()) ntToDelete.delete();
                        printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                        return;
                    }

                    // Marca como cargado el archivo TTL original
                    synchronized (loadTracker) {
                        loadTracker.markFileAsLoaded(file);
                    }

                    ImgpediaLogger.info("Archivo TTL limpiado y convertido exitosamente a N-Triples: " + ntFilePath);
                    printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                } catch (Exception e) {
                    ImgpediaLogger.error("Error procesando archivo TTL: " + e.getMessage());
                    reportBadFile(sanitizedDirectory, file.getName(), "Exception: " + e.getMessage());
                    printProgress(processedCount.incrementAndGet(), totalFiles, "TTL->NT");
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            ImgpediaLogger.error("Thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void reportBadFile(File sanitizedDirectory, String fileName, String errorMsg) {
        try {
            Files.write(
                new File(sanitizedDirectory, "bad_ttl_files.txt").toPath(),
                (fileName + " :: " + errorMsg + System.lineSeparator()).getBytes(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ex) {
            ImgpediaLogger.error("No se pudo escribir el reporte de error para " + fileName + ": " + ex.getMessage());
        }
    }

    public void runTdb1XLoader() {
        String tdbLoc = "/nas_mount/imgpedia/imgpedia_tdb/tdb";
        String exportDir = "/nas_mount/imgpedia/imgpedia_tdb/exports";
        String sanitizedDir = exportDir + File.separator + "sanitized";
        String logFile = DB + File.separator + "tdb1_xloader.log";

        File exportDirectory = new File(exportDir);
        File sanitizedDirectory = new File(sanitizedDir);
        
        // Busca recursivamente todos los archivos .nt EXCLUYENDO sanitized
        List<File> ntFiles = new ArrayList<>();
        findFilesRecursiveExcludingSanitized(exportDirectory, ntFiles, sanitizedDirectory);

        if (ntFiles.isEmpty()) {
            ImgpediaLogger.error("No .nt files found in " + exportDir + " (excluding sanitized)");
            return;
        }

        ImgpediaLogger.info("Found " + ntFiles.size() + " .nt files for xloader (excluding sanitized)");

        List<String> command = new ArrayList<>();
        command.add("tdb1.xloader");
        command.add("--loc=" + tdbLoc);

        // Añade todos los archivos .nt limpios
        for (File ntFile : ntFiles) {
            command.add(ntFile.getAbsolutePath());
        }

        ImgpediaLogger.info("Running tdb1.xloader with " + ntFiles.size() + " cleaned files");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(logFile));
        try {
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

    public void validateAllNtFilesWithXLoader(String exportDir, String sanitizedDir) {
        ImgpediaLogger.info("Validando archivos .nt usando tdb1.xloader en " + exportDir + " ...");
        File exportDirectory = new File(exportDir);
        File sanitizedDirectory = new File(sanitizedDir);
        if (!sanitizedDirectory.exists()) sanitizedDirectory.mkdirs();

        // Busca recursivamente todos los archivos .nt EXCLUYENDO sanitized
        List<File> ntFiles = new ArrayList<>();
        findFilesRecursiveExcludingSanitized(exportDirectory, ntFiles, sanitizedDirectory);

        if (ntFiles.isEmpty()) {
            ImgpediaLogger.info("No .nt files to validate en " + exportDir + " (excluding sanitized)");
            return;
        }

        final int THREADS = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        final int totalFiles = ntFiles.size();
        final AtomicInteger processedCount = new AtomicInteger(0);
        final AtomicInteger corruptedCount = new AtomicInteger(0);
        final AtomicInteger okCount = new AtomicInteger(0);

        File errorLog = new File(sanitizedDirectory, "nt_xloader_validation_log.log");
        File validationDir = new File(sanitizedDirectory, "validation_temp");
        if (!validationDir.exists()) validationDir.mkdirs();

        ImgpediaLogger.info("Archivos .nt a validar con tdb2.xloader: " + totalFiles + " (excluyendo sanitized)");

        for (File ntFile : ntFiles) {
            executor.submit(() -> {
                boolean isCorrupted = false;
                StringBuilder errorMsg = new StringBuilder();
                String tempTdbDir = null;
                String logFile = null;

                try {
                    // Crea directorio temporal único para esta validación
                    tempTdbDir = new File(validationDir, "tdb_" + Thread.currentThread().getId() + "_" + System.currentTimeMillis()).getAbsolutePath();
                    logFile = new File(validationDir, "xloader_" + ntFile.getName() + "_" + System.currentTimeMillis() + ".log").getAbsolutePath();

                    // Ejecuta tdb1.xloader SOLO con este archivo
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
                    // Limpia directorios temporales
                    try {
                        if (tempTdbDir != null) deleteDirectory(new File(tempTdbDir));
                        if (logFile != null) new File(logFile).delete();
                    } catch (Exception cleanupEx) {
                        ImgpediaLogger.warn("Failed to cleanup temp directories for " + ntFile.getName() + ": " + cleanupEx.getMessage());
                    }
                }

                // Si hay corrupción, mueve a sanitized
                if (isCorrupted) {
                    corruptedCount.incrementAndGet();
                    ImgpediaLogger.error("CORRUPTED FILE DETECTED: " + ntFile.getName() + " - Moving to sanitized...");

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

                    // Log del error
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
                            ImgpediaLogger.error("No se pudo escribir el log de validación para " + ntFile.getName() + ": " + logEx.getMessage());
                        }
                    }
                }

                int done = processedCount.incrementAndGet();
                if (done % 10 == 0 || done == totalFiles) {
                    ImgpediaLogger.info("[nt xloader Validation] Progreso: " + done + "/" + totalFiles +
                            " | OK: " + okCount.get() +
                            " | Corrupted: " + corruptedCount.get());
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            ImgpediaLogger.error("Thread interrupted during nt xloader validation: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        // LIMPIEZA FINAL
        try {
            if (validationDir.exists()) {
                deleteDirectory(validationDir);
                ImgpediaLogger.info("Cleaned up validation directory: " + validationDir.getAbsolutePath());
            }
        } catch (Exception ex) {
            ImgpediaLogger.warn("Failed to cleanup main validation directory: " + ex.getMessage());
        }

        ImgpediaLogger.info("=== VALIDACIÓN FINALIZADA ===");
        ImgpediaLogger.info("Total OK: " + okCount.get());
        ImgpediaLogger.info("Total Corrupted: " + corruptedCount.get());
        ImgpediaLogger.info("Total Files: " + totalFiles);

        if (corruptedCount.get() > 0) {
            ImgpediaLogger.error("¡¡¡ SE ENCONTRARON " + corruptedCount.get() + " ARCHIVOS CORRUPTOS !!!");
            ImgpediaLogger.error("Archivos movidos a: " + sanitizedDirectory.getAbsolutePath());
            ImgpediaLogger.error("Ver detalles en: " + errorLog.getAbsolutePath());
        }
    }
    // Función auxiliar para eliminar directorios recursivamente
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

    private void findFilesRecursiveExcludingSanitized(File directory, List<File> rtFiles, File sanitizedDirectory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Excluye la carpeta sanitized
                    if (!file.getAbsolutePath().equals(sanitizedDirectory.getAbsolutePath())) {
                        findFilesRecursiveExcludingSanitized(file, rtFiles, sanitizedDirectory);
                    }
                } else if (file.getName().endsWith(".nt")) {
                    rtFiles.add(file);
                }
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