package it.unicam.cs.bdslab.tarnas.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.biojava.nbio.structure.Structure;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class DockerController {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.controller.DockerController");

    private static DockerController instance = new DockerController();

    private CreateContainerResponse container;
    private final DockerClient dockerClient;
    private final String preprocessingPath = "/data/preprocessed";
    private final String mappingsPath = "/data/mappings";
    private final String bundlesPath = "/data/bundles";
    private Path sharedFolder;
    private final BioJavaController bioJavaController = BioJavaController.getInstance();

    private DockerController() {
        // Setup Docker Client
        DefaultDockerClientConfig.Builder config = DefaultDockerClientConfig.createDefaultConfigBuilder();
        dockerClient = DockerClientBuilder.getInstance(config).build();
    }

    public int buildDockerContainerBy(File dockerContext, String imageName, String imageTag, String containerName, Path sharedFolder) throws IOException, InterruptedException {
        this.sharedFolder = sharedFolder;
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
        makeDirInContainer(container.getId(), preprocessingPath);

        // mappings folder
        makeDirInContainer(container.getId(), mappingsPath);

        // bundles folder
        makeDirInContainer(container.getId(), bundlesPath);

        // --- process exactly ONE CSV in sharedFolder ---
        Path csv = pickSingleCsv();
        if (csv == null) {
            logger.info("No CSV file found in " + sharedFolder + " — nothing to process.");
            return 0;
        }
        logger.info("Using CSV: " + csv.getFileName());

        processCsvAndFilterPdbs(csv);
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
    private Path pickSingleCsv() throws IOException {
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
    private void processCsvAndFilterPdbs(Path csvFile) throws IOException {
        var preprocessedFolder = sharedFolder.resolve("preprocessed");
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
                String chain = cols[1].trim();  // e.g., "A;B"

                // Host path for reading (bind of /data)
                Path fileToFilter = sharedFolder.resolve(pdbID + ".pdb");
                var isPDB = true;

                if (!Files.exists(fileToFilter)) {
                    logger.info("Try to download the PDB file using PDB ID");
                    try {
                        fileToFilter = bioJavaController.downloadPDB(pdbID, String.valueOf(sharedFolder));
                        if (fileToFilter.toString().endsWith("cif")) {
                            logger.info("CIF format recognized");
                            isPDB = false;
                            this.beem(pdbID + ".cif");
                            this.moveFiles(pdbID);
                        }
                    } catch (Exception e) {
                        logger.severe("ERROR: " + e);
                        continue;
                    }
                }
                // preprocessing
                try {
                    if (isPDB) {
                        filterPDB(chain, pdbID, preprocessedFolder, fileToFilter);
                    } else {
                        filterCIF(chain, pdbID, preprocessedFolder);
                    }
                } catch (Exception e) {
                    logger.severe("Failed processing row: " + line + " - " + e.getMessage() + " " + e);
                }
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

    public int buildxDockerContainerBy(File dockerFile, String imageName, String imageTag, String containerName) throws IOException, InterruptedException {
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

    public void beem(String cifFile) throws InterruptedException {
        logger.info("USING BeEM to convert " + cifFile + " to PDB");
        // call BeEM
        String shellCmd = "cd /data && /home/BeEM/BeEM" + " " + cifFile;

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
        //this.buildMappingsCSV();
    }


    /**
     * Takes the PDB ID related to the CIF file and saves mappings and bundles under mappingsPath and bundlesPath.
     *
     * @param pdbID
     */
    private void moveFiles(String pdbID) throws Exception {
        var originalMappingPath = sharedFolder.resolve(pdbID.toLowerCase() + "-chain-id-mapping.txt");
        var formattedMappingPath = originalMappingPath.getParent().resolve(pdbID + "-pdb-mapping.csv");
        // reformat mapping
        var bundles = reformatCSV(originalMappingPath, formattedMappingPath);
        // move bundles using mapping
        for (var b : bundles) {
            var target = sharedFolder.resolve("bundles").resolve(b.getFileName());
            Files.move(b, target, StandardCopyOption.REPLACE_EXISTING);
        }
        // move mapping
        Files.move(formattedMappingPath, sharedFolder.resolve("mappings").resolve(formattedMappingPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    private Set<Path> reformatCSV(Path inputPath, Path outputPath) {
        try (BufferedReader reader = Files.newBufferedReader(inputPath);
             BufferedWriter writer = Files.newBufferedWriter(outputPath)) {

            writer.write("File,New_chain_ID,Original_chain_ID");
            writer.newLine();

            String currentFile = null;
            var bundlePaths = new HashSet<Path>();
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip header and empty lines
                if (line.isEmpty() || line.contains("New chain ID")) {
                    continue;
                }

                // Detect new PDB section (e.g., "3j6b-pdb-bundle1.pdb:")
                if (line.contains(".pdb:")) {
                    currentFile = line.substring(0, line.length() - 1).trim();
                    bundlePaths.add(inputPath.getParent().resolve(currentFile));
                    continue;
                }

                // Expect two columns separated by spaces
                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    writer.write(String.format("%s,%s,%s", currentFile, parts[0], parts[1]));
                    writer.newLine();
                }
            }

            Files.delete(inputPath);

            System.out.println("Reformatted CSV written to: " + outputPath.toAbsolutePath());
            System.out.println("Deleted original mapping: " + inputPath.toAbsolutePath());
            return bundlePaths;
        } catch (IOException e) {
            System.err.println("Error processing " + inputPath + ": " + e.getMessage());
            return null;
        }
    }

    public static DockerController getInstance() {
        if (instance == null) instance = new DockerController();
        return instance;
    }


    private void filterPDB(String chain, String pdbID, Path preprocessedFolder, Path src) throws Exception {
        var filteredFiles = chain.equals("*")
                ? bioJavaController.filterByStar(src)
                : bioJavaController.filterById(src, chain);

        for (var f : filteredFiles) {
            save(f, preprocessedFolder, pdbID);
        }
    }

    private void filterCIF(String chain, String pdbID, Path preprocessedFolder) throws Exception {
        var mapping = sharedFolder.resolve("mappings").resolve(pdbID + "-pdb-mapping.csv");
        var bundles = sharedFolder.resolve("bundles");

        try (Reader reader = Files.newBufferedReader(mapping)) {
            var format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();

            var records = format.parse(reader);

            var newChainIds = new HashMap<String, String>();
            var originalChainIds = new HashMap<String, String>();

            for (var r : records) {
                var file = r.get("File");
                var newChainId = r.get("New_chain_ID");
                var originalChainId = r.get("Original_chain_ID");

                // skip rows not matching the specified chain (unless '*' is used)
                if (!chain.equals("*") && !chain.contains(originalChainId)) {
                    continue;
                }

                // append newChainId with semicolon
                newChainIds.merge(file, newChainId, (oldVal, newVal) -> oldVal + ";" + newVal);

                // store mapping from new to original chain
                originalChainIds.put(newChainId, originalChainId);
            }

            // Process each file
            for (var entry : newChainIds.entrySet()) {
                var bundle = entry.getKey();
                var newChains = entry.getValue();

                var filteredFiles = bioJavaController.filterById(bundles.resolve(bundle), newChains);

                for (var f : filteredFiles) {
                    save(f, preprocessedFolder, pdbID, originalChainIds);
                }
            }
        }
    }

    private void save(Structure f, Path preprocessedFolder, String pdbID) throws Exception {
        var chainId = f.getChains().get(0).getId();
        var dst = preprocessedFolder.resolve(pdbID
                + "_"
                + chainId
                + ".pdb");
        bioJavaController.save(f, dst);
        logger.info("Wrote filtered PDB: " + dst);
    }

    private void save(Structure f, Path preprocessedFolder, String pdbID, Map<String, String> originalChainIds) throws Exception {
        var newChainId = f.getChains().get(0).getId();
        var originalChainId = originalChainIds.get(newChainId);
        var dst = preprocessedFolder.resolve(pdbID
                + "_"
                + originalChainId
                + "_"
                + newChainId
                + ".pdb");
        bioJavaController.save(f, dst);
        logger.info("Wrote filtered PDB: " + dst);
    }
}