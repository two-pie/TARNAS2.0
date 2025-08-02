package it.unicam.cs.bdslab.tarnas.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import it.unicam.cs.bdslab.tarnas.view.HomeController;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class DockerController {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.controller.DockerController");

    private static DockerController instance = new DockerController();

    private final String X3DNA_containerName = "x3dna-container";
    private final String X3DNA_imageName = "x3dna-image";

    private CreateContainerResponse container;
    private final DockerClient dockerClient;

    private DockerController() {
        // Setup Docker Client
        DefaultDockerClientConfig.Builder config = DefaultDockerClientConfig.createDefaultConfigBuilder();
        dockerClient = DockerClientBuilder.getInstance(config).build();
    }

    public void init(String imageName, String imageTag, Path sharedFolder, Stage primaryStage) throws IOException, InterruptedException {

        // Define paths and tags
        File dockerContext = new File("./docker/all-tools");  // Make sure this contains the Dockerfile

        // Build the image
        List<Image> images = dockerClient.listImagesCmd().exec();
        boolean imageExists = false;
        for (Image image : images) {
            if (!imageExists) {
                String[] tags = image.getRepoTags();
                if (tags != null) {
                    for (String tag : tags) {
                        if ((imageName + ":" + imageTag).equals(tag)) {
                            imageExists = true;
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


        container = dockerClient.createContainerCmd(imageName).withName("tarnas2.0-container").withHostConfig(hostConfig).exec();

        // Start the container
        dockerClient.startContainerCmd(container.getId()).exec();
        logger.info("Container started: " + container.getId());

        // Stream container logs
        /*dockerClient.logContainerCmd(container.getId())
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        System.out.print(new String(frame.getPayload()));
                    }
                }).awaitCompletion();*/

        this.initX3DNAContainer(sharedFolder);

    }

    public void initX3DNAContainer(Path sharedFolder) throws IOException, InterruptedException {
        File dockerfile = new File("./docker/x3dna-tool/Dockerfile");
        File contextDir = dockerfile.getParentFile(); // "./docker/x3dna-tool/"

        // Check if the image already exists
        Process checkImage = new ProcessBuilder("docker", "images", "-q", this.X3DNA_imageName)
                .redirectErrorStream(true)
                .start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(checkImage.getInputStream()));
        String imageId = reader.readLine();
        checkImage.waitFor();

        if (imageId == null || imageId.isEmpty()) {
            logger.info("Building X3DNA image...");
            // Create buildx builder
            new ProcessBuilder("docker", "buildx", "create", "--use")
                    .inheritIO().start().waitFor();

            // Build image
            new ProcessBuilder(
                    "docker", "buildx", "build",
                    "--platform", "linux/amd64",
                    "-f", dockerfile.getAbsolutePath(),
                    "-t", this.X3DNA_imageName,
                    "--load",
                    contextDir.getAbsolutePath()
            ).inheritIO().start().waitFor();
            logger.info("Image built: " + this.X3DNA_imageName);
        } else {
            logger.info("Image " + this.X3DNA_imageName + " already exists. Skipping build.");
        }

        // Check and remove existing container
        Process checkContainer = new ProcessBuilder("docker", "ps", "-a", "-q", "-f", "name=" + this.X3DNA_containerName)
                .redirectErrorStream(true)
                .start();

        BufferedReader containerReader = new BufferedReader(new InputStreamReader(checkContainer.getInputStream()));
        String existingContainerId = containerReader.readLine();
        checkContainer.waitFor();

        if (existingContainerId != null && !existingContainerId.isEmpty()) {
            new ProcessBuilder("docker", "rm", "-f", this.X3DNA_containerName)
                    .inheritIO().start().waitFor();
        }

        // Run container with shared volume
        new ProcessBuilder(
                "docker", "run",
                "--platform", "linux/amd64",
                "--name", this.X3DNA_containerName,
                "-v", sharedFolder.toAbsolutePath() + ":/data",
                "-dit",
                this.X3DNA_imageName,
                "bash"
        ).inheritIO().start().waitFor();

        logger.info("Container started with shared folder: " + this.X3DNA_containerName);
    }

    public void stopX3DNAContainer() throws IOException, InterruptedException {
        new ProcessBuilder("docker", "stop", this.X3DNA_containerName)
                .inheritIO().start().waitFor();
    }


    public void bindHostAndContainer() {
        // TODO: capire se si possono fare diversi bind in diversi momenti nello stesso container
        // TODO: capire se smontare la shared folder oppure no

        // se si fa questo metodo, verrà richiamato ogni volta dentro addFolder
    }

    public boolean isContainerRunning() {
        return this.container != null && this.dockerClient.inspectContainerCmd(container.getId())
                .exec()
                .getState()
                .getRunning();
    }

    public void stopContainer() {
        dockerClient.stopContainerCmd(container.getId()).exec();
        logger.info("Container finished execution.");
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
        String shellCmd = "mkdir -p /data/rnapolis-output &&" +
                "for file in /data/*.pdb; do " +
                "    filename=$(basename \"$file\");" +
                "    name=\"${filename%.}\";" +
                "    annotator -b \"/data/rnapolis-output/${name}.bpseq\" \"$file\";" +
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

    public void fr3d() {
        // TODO: chiarire dubbio codici PDB
    }

    public void x3dna() throws InterruptedException, IOException {
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

        new ProcessBuilder("docker", "exec", this.X3DNA_containerName, "bash", "-c", shellCmd)
                .inheritIO().start().waitFor();
    }

    public static DockerController getInstance() {
        if (instance == null) instance = new DockerController();
        return instance;
    }
}