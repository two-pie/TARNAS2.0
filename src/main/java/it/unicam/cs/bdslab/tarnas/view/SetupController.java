package it.unicam.cs.bdslab.tarnas.view;

import it.unicam.cs.bdslab.tarnas.Main;
import it.unicam.cs.bdslab.tarnas.controller.IOController;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

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
            Main.instance.openHome();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Setup error", e.getMessage());
        }
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