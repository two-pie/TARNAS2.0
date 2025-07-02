package it.unicam.cs.bdslab.tarnas.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
