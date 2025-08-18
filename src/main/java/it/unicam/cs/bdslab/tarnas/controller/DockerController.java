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
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

// TODO: unmount volumes, remove generated containers after using buildx

public class DockerController {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.controller.DockerController");

    private static DockerController instance = new DockerController();

    private CreateContainerResponse container;
    private final DockerClient dockerClient;

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

        return 1;
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
                .map(com.github.dockerjava.api.model.Container::getId)
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
                + "for file in /data/*.pdb; do "
                + "filename=$(basename \"$file\"); "
                + "./rnaview \"$file\"; "
                + "find /data -maxdepth 1 -type f -name \"${filename%.*}.*\" -newer \"$file\" -exec mv {} /data/rnaview-output/ \\;; "
                + "done";

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCmd).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    public void rnapolisAnnotator() throws InterruptedException {
        String shellCmd =
                "mkdir -p /data/rnapolis-output && " +
                        "for file in /data/*.pdb; do " +
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
                        "for file in /data/*.pdb; do " +
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
                "for file in /data/*.pdb; do " +
                "  filename=$(basename \"$file\"); " +
                "  prefix=\"${filename%.*}\"; " +
                "  ./bpnet.linux \"$file\"; " +
                "  for output in /data/${prefix}*; do " +
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
                        "for file in *.pdb; do " +
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

    public static DockerController getInstance() {
        if (instance == null) instance = new DockerController();
        return instance;
    }
}