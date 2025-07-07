package it.unicam.cs.bdslab.tarnas.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

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

    public void init(String imageName, Path sharedFolder) {

        // Define paths and tags
        File dockerContext = new File("./docker");  // Make sure this contains the Dockerfile

        // Build the image
        List<Image> images = dockerClient.listImagesCmd().exec();
        boolean imageExists = false;
        for (Image image : images) {
            String[] tags = image.getRepoTags();
            if (tags != null) {
                for (String tag : tags) {
                    if (imageName.equals(tag)) {
                        imageExists = true;
                        logger.info("Image found with ID: " + image.getId());
                        break;
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
    }

    public void stopContainer() {
        dockerClient.stopContainerCmd(container.getId()).exec();
        logger.info("Container finished execution.");
    }

    public void rnaView() throws InterruptedException {
        String shellCommand = "mkdir -p /data/rnaview-output && "
                + "cd /home/RNAView/bin && "
                + "for file in /data/*.pdb; do "
                + "filename=$(basename \"$file\"); "
                + "./rnaview \"$file\"; "
                + "find /data -maxdepth 1 -type f -name \"${filename%.*}.*\" -newer \"$file\" -exec mv {} /data/rnaview-output/ \\;; "
                + "done";

        // Create exec command
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("bash", "-c", shellCommand).exec();

        // Start and attach to output
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ExecStartResultCallback(System.out, System.err)).awaitCompletion();
    }

    public void baRNAba(Path sharedFolder){
        // TODO
    }

    public static DockerController getInstance() {
        if (instance == null) instance = new DockerController();
        return instance;
    }

}