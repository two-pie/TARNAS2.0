package it.unicam.cs.bdslab.tarnas.view;

import it.unicam.cs.bdslab.tarnas.Main;
import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.controller.IOController;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class SetupController {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.view.SetupController");

    @FXML
    private TextArea sharedDirectoryTextArea;

    private final IOController ioController = IOController.getInstance();
    private final DockerController dockerController = DockerController.getInstance();

    @FXML
    public void initialize() {
        Path sharedDirectory = ioController.getSharedDirectory();
        if (sharedDirectory != null) {
            sharedDirectoryTextArea.setText(sharedDirectory.toString());
        }
        sharedDirectoryTextArea.setEditable(false);
        sharedDirectoryTextArea.setWrapText(false);
    }

    @FXML
    public void handleBrowseButtonAction() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select shared Docker folder");

        Path currentDirectory = ioController.getSharedDirectory();
        if (currentDirectory != null && Files.isDirectory(currentDirectory)) {
            File initialDirectory = currentDirectory.toFile();
            if (initialDirectory.isDirectory()) {
                directoryChooser.setInitialDirectory(initialDirectory);
            }
        }

        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            sharedDirectoryTextArea.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    public void handleNextButtonAction() {
        String selectedPath = sharedDirectoryTextArea.getText() == null ? "" : sharedDirectoryTextArea.getText().trim();
        if (selectedPath.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No folder selected", "Select a shared folder before continuing.");
            return;
        }

        Path sharedDirectory = Path.of(selectedPath);
        if (!Files.isDirectory(sharedDirectory)) {
            showAlert(Alert.AlertType.ERROR, "Invalid folder", "The selected path is not a valid directory.");
            return;
        }

        try {
            ioController.loadDirectory(sharedDirectory);
            logger.info("Shared folder set to: " + sharedDirectory);
            initializeContainersAndOpenHome(sharedDirectory);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Setup error", e.getMessage());
        }
    }

    private void initializeContainersAndOpenHome(Path sharedDirectory) {
        // Create a custom Stage instead of Alert for better control
        Stage loadingStage = new Stage();
        loadingStage.setTitle("Docker setup");
        loadingStage.initModality(Modality.APPLICATION_MODAL);
        loadingStage.setResizable(false);
        
        Label title = new Label("Initializing Docker containers…");
        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(380);
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        
        VBox vbox = new VBox(10, title, bar);
        vbox.setPadding(new javafx.geometry.Insets(20));
        vbox.setStyle("-fx-alignment: center;");
        
        Scene scene = new javafx.scene.Scene(vbox, 400, 120);
        loadingStage.setScene(scene);

        Task<Integer> taskBuild = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return dockerController.buildDockerContainerBy(
                        new File(HomeController.dockerfileAllToolsPath),
                        HomeController.dockerAllToolsImage,
                        HomeController.dockerAllToolsImageTag,
                        HomeController.dockerAllToolsContainer,
                        sharedDirectory);
            }
        };

        Task<Integer> taskBuildx = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return dockerController.buildxDockerContainerBy(
                        new File(HomeController.dockerfileX3DNAPath),
                        HomeController.dockerX3DNAImage,
                        HomeController.dockerX3DNAImageTag,
                        HomeController.dockerX3DNAContainer);
            }
        };

        taskBuild.setOnRunning(e -> title.setText("Initializing Docker containers… (step 1/2)"));

        taskBuild.setOnSucceeded(e -> {
            Integer r1 = taskBuild.getValue();
            if (r1 == null || r1 != 1) {
                Platform.runLater(() -> {
                    loadingStage.close();
                    showAlert(Alert.AlertType.ERROR, "Setup error", "Failed to initialize all-tools container.");
                });
                return;
            }

            boolean x3dnaAvailable = dockerController.dockerImageExists(HomeController.dockerX3DNAImage)
                    || dockerController.isX3DNABuildContextAvailable(new File(HomeController.dockerfileX3DNAPath));

            if (!x3dnaAvailable) {
                Platform.runLater(() -> {
                    loadingStage.close();
                    Main.instance.openHome();
                });
                return;
            }

            Platform.runLater(() -> title.setText("Initializing Docker containers… (step 2/2)"));
            new Thread(taskBuildx, "setup-docker-buildx").start();
        });

        taskBuild.setOnFailed(e -> {
            Platform.runLater(() -> {
                loadingStage.close();
                String msg = taskBuild.getException() == null ? "Unknown error" : taskBuild.getException().getMessage();
                showAlert(Alert.AlertType.ERROR, "Setup error", msg);
            });
        });

        taskBuildx.setOnSucceeded(e -> {
            Integer r2 = taskBuildx.getValue();
            Platform.runLater(() -> {
                loadingStage.close();
                if (r2 != null && r2 == 1) {
                    Main.instance.openHome();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Setup error", "Failed to initialize X3DNA container.");
                }
            });
        });

        taskBuildx.setOnFailed(e -> {
            Platform.runLater(() -> {
                loadingStage.close();
                String msg = taskBuildx.getException() == null ? "Unknown error" : taskBuildx.getException().getMessage();
                showAlert(Alert.AlertType.ERROR, "Setup error", msg);
            });
        });

        loadingStage.show();
        new Thread(taskBuild, "setup-docker-build").start();
    }

    private Stage getStage() {
        return (Stage) sharedDirectoryTextArea.getScene().getWindow();
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType, content, ButtonType.OK);
        alert.initOwner(getStage());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}