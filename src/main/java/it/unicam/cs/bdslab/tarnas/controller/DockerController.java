package it.unicam.cs.bdslab.tarnas.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// TODO: add Biojava interaction and check if the updated tool functions work with " + this.preprocessingPath + " changes

// TODO: make some experiments with a csv file uploaded with the pdb files!

// TODO: unmount volumes, remove generated containers after using buildx
public class DockerController {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.controller.DockerController");

    private static DockerController instance = new DockerController();

    private CreateContainerResponse container;
    private final DockerClient dockerClient;
    private final String preprocessingPath = "/data/preprocessed";
    private final String mappingsPath = "/data/mappings";
    private final String bundlesPath = "/data/bundles";

    private DockerController() {
        // Setup Docker Client
        DefaultDockerClientConfig.Builder config = DefaultDockerClientConfig.createDefaultConfigBuilder();
        dockerClient = DockerClientBuilder.getInstance(config).build();
    }

    public int buildDockerContainerBy(File dockerContext, String imageName, String imageTag, String containerName, Path sharedFolder) throws IOException, InterruptedException {
        // Build the image
        List<Image> images = dockerClient.listImagesCmd().exec();
        boolean imageExists = false;
        for (Image image : images) {
            if (!imageExists) {
                String[] tags = image.getRepoTags();
                if (tags != null) {
                    for (String tag : tags) {
                        if ((imageName + ":" + imageTag).equals(tag)) {
                            logger.info("Image found with ID: " + image.getId());
                            break;
                        }
                    }
                }
            }
        }

        if (!imageExists) {
            logger.info("Building image...");
            String imageId = dockerClient.buildImageCmd(dockerContext).withTags(Set.of(imageName)).exec(new BuildImageResultCallback()).awaitImageId();
            logger.info("Image built: " + imageId);
        }

        // Define shared folder (host and container paths)
        String hostSharedFolder = new File(sharedFolder.toUri()).getAbsolutePath();  // Ensure it exists
        String containerSharedFolder = "/data";

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withAutoRemove(true)
                .withBinds(new Bind(hostSharedFolder, new Volume(containerSharedFolder)) // ./shared → /data
                );


        container = dockerClient.createContainerCmd(imageName).withName(containerName).withHostConfig(hostConfig).exec();

        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        logger.info("Container started: " + container.getId());

        // --- ensure /data/preprocessed inside the container (not on host explicitly) ---
        makeDirInContainer(container.getId(), "/data/preprocessed");

        // mappings folder
        makeDirInContainer(container.getId(), "/data/mappings");

        // bundles folder
        makeDirInContainer(container.getId(), "/data/bundles");

        // --- process exactly ONE CSV in sharedFolder ---
        Path csv = pickSingleCsv(sharedFolder);
        if (csv == null) {
            logger.info("No CSV file found in " + sharedFolder + " — nothing to process.");
            return 0;
        }
        logger.info("Using CSV: " + csv.getFileName());

        processCsvAndFilterPdbs(csv, sharedFolder);

        return 1;
    }

    private void makeDirInContainer(String containerId, String dir) throws IOException, InterruptedException {
        String[] mkdirCmd = {"mkdir", "-p", dir};
        ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(mkdirCmd)
                .exec();

        dockerClient.execStartCmd(execCreate.getId())
                .exec(new ExecStartResultCallback(System.out, System.err))
                .awaitCompletion();

        logger.info("Ensured directory exists in container: " + dir);
    }

    /**
     * Pick exactly one CSV in the folder:
     * - If none: return null.
     * - If multiple: pick the first after sorting by filename, and log a warning.
     */
    private Path pickSingleCsv(Path sharedFolder) throws IOException {
        List<Path> csvs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sharedFolder, "*.csv")) {
            for (Path p : ds) csvs.add(p);
        }
        if (csvs.isEmpty()) return null;

        csvs.sort(Comparator.comparing(p -> p.getFileName().toString()));
        if (csvs.size() > 1)
            logger.severe("Multiple CSV files found in " + sharedFolder + " — using the first: " + csvs.get(0).getFileName());
        return csvs.get(0);
    }

    /**
     * Reads the single CSV and processes potentially many PDB rows.
     * CSV: col0 = pdbPath (relative to /data), col1 = chainFilter (e.g., "A;B").
     * Output: /data/preprocessed/<basename>_filtered.pdb
     */
    private void processCsvAndFilterPdbs(Path csvFile, Path sharedFolder) throws IOException {
        Path preprocessedHost = sharedFolder.resolve("preprocessed");
        var bioJavaController = BioJavaController.getInstance();
        Set<String> csvPaths = new HashSet<>();
        try (BufferedReader br = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String line;
            boolean headerSkipped = false;

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;

                if (!headerSkipped && looksLikeHeader(line)) {
                    headerSkipped = true;
                    continue;
                }

                String[] cols = parseRow(line);
                if (cols.length < 2) {
                    logger.severe("Skipping row (needs at least 2 columns): {}" + line);
                    continue;
                }

                String pdbID = cols[0].trim();   // path relative to /data
                String chainFilter = cols[1].trim();  // e.g., "A;B"

                // Host path for reading (bind of /data)
                Path pdbHostPath = sharedFolder.resolve(pdbID + ".pdb");
                csvPaths.add(pdbID);
                if (!Files.exists(pdbHostPath)) {
                    logger.severe("PDB: " + pdbID + " not found on host: " + pdbHostPath);
                    logger.info("Try to download the PDB file using PDB ID");
                    try {
                        pdbHostPath = bioJavaController.downloadPDB(pdbID, String.valueOf(sharedFolder));
                        if (pdbHostPath.toString().endsWith("cif")) {
                            logger.info("CIF format recognized");
                            // if cif -> if so, call BeEM
                            this.beem(pdbHostPath.toString());
                            // call methods to build mappings.csv and then move mappings and bundles
                            //this.moveFiles();
                        }
                    } catch (Exception e) {
                        logger.severe("PDB ID not valid");
                        logger.severe("ERROR: " + e);
                        continue;
                    }
                }
                try {
                    var filter = bioJavaController.getDefaultChainFilter(chainFilter);
                    var filteredFiles = bioJavaController.readPDBFile(pdbHostPath.toAbsolutePath().toString(), filter);
                    bioJavaController.writeStructuresWithSingleChain(filteredFiles, preprocessedHost.resolve(pdbID).toAbsolutePath().toString());
                    logger.info("Wrote filtered PDB: " + pdbHostPath);
                } catch (Exception e) {
                    logger.severe("Failed processing row: " + line + " - " + e.getMessage() + " " + e);
                }
            }
            // read each file under shared folder
            // apply a filter to exclude all the files belong to the csvPaths set
            // copy the remaining files
            try {
                Files.list(sharedFolder)
                        .filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .filter(p -> p.toString().endsWith(".pdb"))
                        .filter(p -> !csvPaths.contains(p.getFileName().toString().substring(0, p.getFileName().toString().lastIndexOf("."))))
                        .forEach(p -> {
                            logger.info("Reading file: " + p);
                            try {
                                Files.copy(p, preprocessedHost.resolve(p.getFileName()));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String[] parseRow(String line) {
        // Simple CSV split; swap with a CSV library if quoting/commas are expected.
        return line.split(",", -1);
    }

    private static boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("id") || lower.contains("chain");
    }

    public int buildxDockerContainerBy(File dockerFile, String imageName, String imageTag, String containerName, Path sharedFolder) throws IOException, InterruptedException {
        File contextDir = dockerFile.getParentFile();

        // Check if the image already exists
        Process checkImage = new ProcessBuilder("docker", "images", "-q", imageName)
                .redirectErrorStream(true)
                .start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(checkImage.getInputStream()));
        String imageId = reader.readLine();
        checkImage.waitFor();

        if (imageId == null || imageId.isEmpty()) {
            logger.info("Building " + imageName + " image...");
            // Create buildx builder
            new ProcessBuilder("docker", "buildx", "create", "--use")
                    .inheritIO().start().waitFor();

            // Build image
            new ProcessBuilder(
                    "docker", "buildx", "build",
                    "--platform", "linux/amd64",
                    "-f", dockerFile.getAbsolutePath(),
                    "-t", imageName,
                    "--load",
                    contextDir.getAbsolutePath()
            ).inheritIO().start().waitFor();
            logger.info("Image built: " + imageName);
        } else {
            logger.info("Image " + imageName + " already exists. Skipping build.");
        }

        // Check and remove existing container
        Process checkContainer = new ProcessBuilder("docker", "ps", "-a", "-q", "-f", "name=" + containerName)
                .redirectErrorStream(true)
                .start();

        BufferedReader containerReader = new BufferedReader(new InputStreamReader(checkContainer.getInputStream()));
        String existingContainerId = containerReader.readLine();
        checkContainer.waitFor();

        if (existingContainerId != null && !existingContainerId.isEmpty()) {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .inheritIO().start().waitFor();
        }

        // Run container with shared volume
        new ProcessBuilder(
                "docker", "run",
                "--platform", "linux/amd64",
                "--name", containerName,
                "-v", sharedFolder.toAbsolutePath() + ":/data",
                "-dit",
                imageName,
                "bash"
        ).inheritIO().start().waitFor();

        logger.info("Container started with shared folder: " + containerName);

        makeDirInContainer(this.resolveContainerId(containerName), "/data/preprocessed");

        return 1;
    }

    /**
     * Stop a container by name or ID.
     *
     * @param containerNameOrId e.g. "my-container" or a container ID/prefix
     * @param timeoutSeconds    null to use daemon default; otherwise grace period before SIGKILL
     * @return true if a stop was issued and the exit status looked OK; false otherwise
     */
    public boolean stopContainerByNameOrId(String containerNameOrId, Integer timeoutSeconds) {
        Objects.requireNonNull(containerNameOrId, "containerNameOrId");

        // 1) Try Docker Java API first
        try {
            // resolve the container ID by name or ID/prefix
            String resolvedId = resolveContainerId(containerNameOrId);
            if (resolvedId != null) {
                var cmd = dockerClient.stopContainerCmd(resolvedId);
                if (timeoutSeconds != null) {
                    try {
                        cmd.withTimeout(timeoutSeconds);
                    } catch (Throwable ignored) {
                        // older docker-java may not have withTimeout; ignore
                    }
                }
                cmd.exec();
                logger.info("Stopped container via API: {}" + resolvedId);
                return true;
            } else
                logger.severe("Container not found via API: {}" + containerNameOrId);
        } catch (Exception apiErr) {
            logger.severe("API stop failed (will try CLI): {}" + apiErr.toString());
        }

        // 2) Fallback to CLI: docker stop <nameOrId>
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stop",
                    timeoutSeconds != null ? String.format("--time=%d", timeoutSeconds) : "",
                    containerNameOrId);
            // remove empty arg if no timeout
            pb.command().removeIf(String::isBlank);
            Process p = pb.inheritIO().start();
            int code = p.waitFor();
            if (code == 0) {
                logger.info("Stopped container via CLI: {}" + containerNameOrId);
                return true;
            } else
                logger.severe("CLI 'docker stop' exited with {}" + code);
        } catch (IOException | InterruptedException cliErr) {
            Thread.currentThread().interrupt();
            logger.severe("CLI stop failed: {}" + cliErr);
        }

        return false;
    }

    /**
     * Resolve a container ID from a name or ID/prefix.
     */
    private String resolveContainerId(String nameOrId) {
        // Try direct inspect (works for ID or full name)
        try {
            logger.info("Container: " + nameOrId + " - RESOLVED ID: " + dockerClient.inspectContainerCmd(nameOrId).exec().getId());
            return dockerClient.inspectContainerCmd(nameOrId).exec().getId();
        } catch (Exception ignored) {
            // not directly resolvable; try listing
        }

        // Names in docker-java come with a leading "/" (e.g. "/my-container")
        String wanted = nameOrId.startsWith("/") ? nameOrId : "/" + nameOrId;

        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec()
                .stream()
                .filter(c ->
                        // match by exact name
                        Arrays.asList(Optional.ofNullable(c.getNames()).orElse(new String[0])).contains(wanted)
                                // or by ID prefix
                                || c.getId().startsWith(nameOrId))
                .map(Container::getId)
                .findFirst()
                .orElse(null);
    }

    public boolean isContainerRunning(String containerName) {
        String containerId = resolveContainerId(containerName);
        if (containerId == null) {
            logger.severe("Container not found: " + containerName);
            return false;
        }

        try {
            return dockerClient.inspectContainerCmd(containerId)
                    .exec()
                    .getState()
                    .getRunning();
        } catch (Exception e) {
            logger.severe("Failed to inspect container: " + containerName);
            return false;
        }
    }

    public void rnaView() throws InterruptedException {
        String shellCmd = "mkdir -p /data/rnaview-output && "
                + "cd /home/RNAView/bin && "
                + "for file in " + this.preprocessingPath + "/*.pdb; do "
                + "filename=$(basename \"$file\"); "
                + "./rnaview \"$file\"; "
                + "find " + this.preprocessingPath + " -maxdepth 1 -type f -name \"${filename%.*}.*\" -newer \"$file\" -exec mv {} /data/rnaview-output/ \\;; "
                + "done";

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    public void rnapolisAnnotator() throws InterruptedException {
        String shellCmd =
                "mkdir -p /data/rnapolis-output && " +
                        "for file in " + this.preprocessingPath + "/*.pdb; do " +
                        "    filename=$(basename \"$file\"); " +
                        "    name=\"${filename%.*}\"; " +
                        "    annotator -e \"$file\" | sed 's/^[ \t]*//' > \"/data/rnapolis-output/${name}.3db\"; " +
                        "done";

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    public void baRNAba() throws InterruptedException {
        String shellCmd =
                "mkdir -p /data/barnaba-output && " +
                        "for file in " + this.preprocessingPath + "/*.pdb; do " +
                        "filename=$(basename \"$file\"); " +
                        "name=\"${filename%.}\"; " +
                        "./barnaba/bin/barnaba ANNOTATE --pdb \"$file\"; " +
                        "mv outfile.ANNOTATE.pairing.out \"/data/barnaba-output/${name}.ANNOTATE.pairing.out\"; " +
                        "mv outfile.ANNOTATE.stacking.out \"/data/barnaba-output/${name}.ANNOTATE.stacking.out\"; " +
                        "done";

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    public void bpnet() throws InterruptedException {
        String shellCmd = "mkdir -p /data/bpnet-output && "
                + "cd /home/bpnet/bin && " +
                "for file in " + this.preprocessingPath + "/*.pdb; do " +
                "  filename=$(basename \"$file\"); " +
                "  prefix=\"${filename%.*}\"; " +
                "  ./bpnet.linux \"$file\"; " +
                "  for output in " + this.preprocessingPath + "/${prefix}*; do " +
                "    [ \"$output\" = \"$file\" ] && continue; " +  // skip the input file
                "    outname=$(basename \"$output\"); " +
                "    mv \"$output\" \"/data/bpnet-output/${prefix}.$outname\"; " +
                "  done; " +
                "done";


        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    // TODO: check if replacing data with " + this.preprocessingPath + " or not
    public void fr3d() throws InterruptedException {
        String shellCmd =
                "mkdir -p /data/fr3d-output && " +
                        "cd /home/fr3d-python/fr3d/classifiers/ && " +
                        "for file in /data/*.pdb; do " +
                        "filename=$(basename \"$file\" .pdb); " +
                        "python NA_pairwise_interactions.py -o /data/fr3d-output/ \"/data/${filename}.cif\"; " +
                        "done && " +
                        "rm -f /data/*.gz && " +
                        "rm -rf \"/home/fr3d-python/fr3d/classifiers/C:\\\\Users\\\\zirbel\\\\Documents\\\\FR3D\\\\PDBFiles\\\\\"";
        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    public void x3dnaBy(String containerName) throws InterruptedException, IOException {
        String shellCmd =
                "set -e; cd /data; mkdir -p x3dna-output; " +
                        "for file in " + this.preprocessingPath + "/*.pdb; do " +
                        "  filename=$(basename \"$file\"); " +
                        "  prefix=\"${filename%.*}\"; " +
                        "  find_pair \"$file\"; " +
                        "  for output in bestpairs.pdb bp_order.dat col_chains.scr col_helices.scr hel_regions.pdb ref_frames.dat; do " +
                        "    if [ -f \"$output\" ]; then " +
                        "      mv \"$output\" \"x3dna-output/${prefix}_$output\"; " +
                        "    fi; " +
                        "  done; " +
                        "done";

        new ProcessBuilder("docker", "exec", containerName, "bash", "-c", shellCmd)
                .inheritIO().start().waitFor();
    }

    public void beem(String cifFilePath) throws InterruptedException {
        logger.info("USING BeEM to convert " + cifFilePath + " to PDB");
        // call BeEM
        String shellCmd = "./home/BeEM/BeEM " + cifFilePath;

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
        this.buildMappingsCSV();
    }

    /**
     * Builds the csv file from the cif mapping file generated by BeEM.
     *
     * @param cifMappingFilePath
     */
    private void buildMappingsCSV(String cifMappingFilePath) {

    }

    /**
     * Takes the PDB ID related to the CIF file and saves mappings and bundles under mappingsPath and bundlesPath.
     *
     * @param pdbID
     */
    private void moveFiles(String pdbID) {

    }

    public static DockerController getInstance() {
        if (instance == null) instance = new DockerController();
        return instance;
    }
}