package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
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
    private static final int FILES_PER_DATASET = 100;
    private static final int THREADS = 20; // O ajusta según tu hardware

    private Dataset dataset;
    private Model model;
    private RdfLoadTracker loadTracker;

    // Para manejar datasets temporales
    private final List<String> tempDatasetPaths = Collections.synchronizedList(new ArrayList<>());

    public RdfConfiguration() {}

    @PostConstruct
    public void initRdfModel() {
        ImgpediaLogger.info("Initializing RDF model...");
        this.loadTracker = new RdfLoadTracker(TRACKER_FILE);

        File dbDir = new File(DB);
        if (!dbDir.exists()) dbDir.mkdirs();

        String[] directories = getRdfDirectories();
        List<File> allFiles = new ArrayList<>();
        for (String directoryPath : directories) {
            File dir = new File(directoryPath);
            File[] files = dir.listFiles((dir1, name) ->
                    name.endsWith(".ttl") || name.endsWith(".rdf") || name.endsWith(".tar.gz"));
            if (files != null) {
                for (File f : files) allFiles.add(f);
            }
        }
        int totalFiles = allFiles.size();
        ImgpediaLogger.info("Total files to process: " + totalFiles);

        // Divide archivos en batches
        List<List<File>> batches = new ArrayList<>();
        for (int i = 0; i < allFiles.size(); i += FILES_PER_DATASET) {
            int end = Math.min(i + FILES_PER_DATASET, allFiles.size());
            batches.add(allFiles.subList(i, end));
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<String>> futures = new ArrayList<>();

        // 1. Procesar los batches en paralelo
        for (int i = 0; i < batches.size(); i++) {
            final int datasetIndex = i;
            final List<File> batch = new ArrayList<>(batches.get(i));
            futures.add(executor.submit(() -> {
                String tempPath = DB + File.separator + "temp_" + datasetIndex;
                Dataset tempDataset = null;
                Model tempModel = null;
                List<File> loadedFiles = new ArrayList<>();
                try {
                    new File(tempPath).mkdirs();
                    tempDataset = TDB2Factory.connectDataset(tempPath);
                    tempDataset.begin(ReadWrite.WRITE);
                    tempModel = tempDataset.getDefaultModel();
                    for (File file : batch) {
                        if (loadTracker.isFileLoaded(file)) {
                            ImgpediaLogger.info("[Thread-" + datasetIndex + "] Skipping already loaded file: " + file.getName());
                            continue;
                        }
                        boolean success = false;
                        try (InputStream inputStream = new FileInputStream(file)) {
                            ImgpediaLogger.info("[Thread-" + datasetIndex + "] Loading file: " + file.getAbsolutePath());
                            RDFParser.create()
                                    .source(inputStream)
                                    .lang(RDFLanguages.filenameToLang(file.getName()))
                                    .errorHandler(createErrorHandler())
                                    .parse(new EncodeIRI(tempModel));
                            ImgpediaLogger.info("[Thread-" + datasetIndex + "] Successfully loaded file content: " + file.getName());
                            success = true;
                        } catch (Exception e) {
                            ImgpediaLogger.error("[Thread-" + datasetIndex + "] Error loading file: " + file.getAbsolutePath() + " - " + e.getMessage());
                        }
                        if (success) loadedFiles.add(file);
                    }
                    tempDataset.commit();
                    ImgpediaLogger.info("[Thread-" + datasetIndex + "] Commit realizado para batch.");
                    // Marca los archivos como cargados solo después del commit
                    synchronized (loadTracker) {
                        for (File loaded : loadedFiles) {
                            loadTracker.markFileAsLoaded(loaded);
                            ImgpediaLogger.info("[Thread-" + datasetIndex + "] Marked file as loaded after commit: " + loaded.getName());
                        }
                    }
                    return tempPath;
                } catch (Exception e) {
                    ImgpediaLogger.error("[Thread-" + datasetIndex + "] Error en batch: " + e.getMessage());
                    return null;
                } finally {
                    if (tempDataset != null) {
                        if (tempDataset.isInTransaction()) {
                            tempDataset.abort();
                            tempDataset.end();
                        }
                        tempDataset.close();
                    }
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ImgpediaLogger.error("Batch processing interrupted: " + e.getMessage());
        }

        // Recoge los paths de los datasets temporales exitosos
        for (Future<String> future : futures) {
            try {
                String tempPath = future.get();
                if (tempPath != null) tempDatasetPaths.add(tempPath);
            } catch (Exception e) {
                ImgpediaLogger.error("Error recuperando resultado de batch: " + e.getMessage());
            }
        }

        // 2. Fusionar datasets temporales en el dataset final
        String finalDatasetPath = DB + File.separator + "final";
        new File(finalDatasetPath).mkdirs();
        Dataset finalDataset = TDB2Factory.connectDataset(finalDatasetPath);
        finalDataset.begin(ReadWrite.WRITE);
        Model finalModel = finalDataset.getDefaultModel();

        int totalTempDatasets = tempDatasetPaths.size();
        int mergedCount = 0;
        ImgpediaLogger.info("Comenzando fusión de " + totalTempDatasets + " datasets temporales...");
        for (String tempPath : tempDatasetPaths) {
            Dataset tempDataset = TDB2Factory.connectDataset(tempPath);
            tempDataset.begin(ReadWrite.READ);
            try {
                finalModel.add(tempDataset.getDefaultModel());
                ImgpediaLogger.info("Merged dataset: " + tempPath + " into final dataset");
            } finally {
                tempDataset.end();
                tempDataset.close();
            }
            // Elimina el dataset temporal del disco
            deleteDirectory(Paths.get(tempPath));
            ImgpediaLogger.info("Deleted temporary dataset: " + tempPath);

            mergedCount++;
            printProgress(mergedCount, totalTempDatasets, "Fusión de datasets");
        }

        finalDataset.commit();
        finalDataset.end();

        // Asigna el dataset y modelo final a los beans
        this.dataset = finalDataset;
        this.model = finalDataset.getDefaultModel();

        ImgpediaLogger.info("Fusion complete. Final dataset at: " + finalDatasetPath);
    }

    private void printProgress(int current, int total, String etapa) {
        int percent = (int) ((current * 100.0f) / total);
        ImgpediaLogger.info("[" + etapa + "] Progreso: " + current + "/" + total + " (" + percent + "%)");
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