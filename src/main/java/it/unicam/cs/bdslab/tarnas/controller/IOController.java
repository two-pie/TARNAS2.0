package it.unicam.cs.bdslab.tarnas.controller;

import it.unicam.cs.bdslab.tarnas.models.StructureInfo;
import it.unicam.cs.bdslab.tarnas.models.StructureStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Singleton controller for loading, saving, and packaging RNA files.
 * Exceptions are propagated upwards.
 */
public class IOController {

    private static final IOController instance = new IOController();
    private Path sharedDirectory;

    private IOController() {
    }

    public static IOController getInstance() {
        return instance;
    }

    /**
     * Loads all regular files from a directory.
     */
    public void loadDirectory(Path p) throws IOException {
        this.sharedDirectory = p;
    }

    public Path getSharedDirectory() {
        return this.sharedDirectory;
    }

    /**
     * Loads molecules from a CSV file where col0 is molecule ID and col1 is chain filter.
     */
    public List<StructureInfo> loadMoleculesFromCsv(Path csvPath) throws IOException {
        if (csvPath == null || !Files.isRegularFile(csvPath)) {
            throw new IOException("CSV file not found: " + csvPath);
        }

        List<StructureInfo> result = new ArrayList<>();
        LinkedHashSet<String> seenRows = new LinkedHashSet<>();

        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean headerSkipped = false;

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;

                if (!headerSkipped && looksLikeHeader(line)) {
                    headerSkipped = true;
                    continue;
                }

                String[] cols = line.split(",", -1);
                if (cols.length < 2) continue;

                String moleculeId = cols[0].trim();
                String chain = cols[1].trim();
                if (moleculeId.isEmpty() || chain.isEmpty()) continue;

                String dedupKey = moleculeId + "|" + chain;
                if (seenRows.add(dedupKey)) {
                    result.add(new StructureInfo(moleculeId, chain, csvPath.toString(), StructureStatus.LOADED));
                }
            }
        }

        return result;
    }

    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase();
        return lower.contains("id") || lower.contains("chain");
    }

    /**
     * Saves and optionally processes RNA files, generates statistics, and zips the result.
     */
    public void saveFiles(List<Path> files) throws IOException {

    }


    private void createZipFile(Path dstPath, String zipFileName, List<Path> generatedFiles) throws IOException {

    }

    public void clear() {
        this.sharedDirectory = null;
    }
}
