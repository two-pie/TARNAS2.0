package it.unicam.cs.bdslab.tarnas;

import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.view.HomeController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

/**
 * TARNAS JavaFX App
 *
 * @author Piero Hierro, Piermichele Rosati
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException, URISyntaxException {
        Parent root = FXMLLoader.load(Objects.requireNonNull(Main.class.getResource("/fxml/home.fxml")));
        stage.setTitle("TARNAS2.0");
        stage.getIcons().add(new Image(String.valueOf(App.class.getResource("/img/tarnas-icon.png").toURI())));
        stage.setScene(new Scene(root));
        stage.setMinWidth(1300);
        stage.setMinHeight(700);
        stage.setOnCloseRequest(event -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to exit?", ButtonType.YES, ButtonType.NO);
            Optional<ButtonType> result = confirmation.showAndWait();

            if (result.isEmpty() || result.get() == ButtonType.NO) {
                event.consume(); // prevents the window from closing
            } else {
                boolean shouldStopContainer = DockerController.getInstance().isContainerRunning();

                if (shouldStopContainer) {
                    Alert infoAlert = new Alert(Alert.AlertType.NONE);
                    infoAlert.setTitle("Closing...");
                    infoAlert.setHeaderText(null);
                    infoAlert.setContentText("Closing the container...");
                    infoAlert.initOwner(stage);
                    infoAlert.initModality(Modality.WINDOW_MODAL);
                    infoAlert.show();

                    new Thread(() -> {
                        DockerController.getInstance().stopContainer();
                        try {
                            DockerController.getInstance().stopX3DNAContainer();
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        Platform.runLater(() -> {
                            infoAlert.close();
                            Platform.exit();
                        });
                    }).start();

                    event.consume();
                } else {
                    Platform.exit(); // just exit immediately
                }
            }
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}