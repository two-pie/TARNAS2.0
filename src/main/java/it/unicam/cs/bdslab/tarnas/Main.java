package it.unicam.cs.bdslab.tarnas;

import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.view.HomeController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * TARNAS JavaFX App
 *
 * @author Piero Hierro, Piermichele Rosati
 */

// TODO: check text inside alert

public class Main extends Application {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.main");

    @Override
    public void start(Stage stage) throws IOException, URISyntaxException {
        Parent root = FXMLLoader.load(Objects.requireNonNull(Main.class.getResource("/fxml/home.fxml")));
        stage.setTitle("TARNAS2.0");
        stage.getIcons().add(new Image(String.valueOf(App.class.getResource("/img/tarnas-icon.png").toURI())));
        stage.setScene(new Scene(root));
        stage.setMinWidth(1300);
        stage.setMinHeight(700);

        stage.setOnCloseRequest(event -> {
            event.consume(); // Prevent automatic closing

            if (showCloseConfirmation()) {
                try {
                    this.stop(); // Call your method that stops containers and cleans up
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                logger.info("Closing the app...");
                Platform.exit();
            }

        });

        stage.show();
    }

    /**
     * Shows ONE alert while stopping both containers. The dialog stays open
     * until both stops finish (success or failure). At 100% the Close button is enabled
     * and the dialog auto-closes after a short delay.
     * <p>
     * Call this from Main.stop() like:
     * stopBothContainersWithOneAlert("x3dna-container", "all-tools-container", 10);
     */
    public void stopBothContainersWithOneAlert(String name1, String name2, Integer timeoutSeconds) {
        if (DockerController.getInstance().isContainerRunning(HomeController.dockerAllToolsContainer) && DockerController.getInstance().isContainerRunning(HomeController.dockerX3DNAContainer)) {
            // Build the alert UI
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Stopping Docker Containers");
            alert.setHeaderText(null);

            Label title = new Label("Stopping containers…");
            ProgressBar bar = new ProgressBar(0);
            bar.setPrefWidth(380);
            Label percent = new Label("0%");

            VBox box = new VBox(10, title, bar, percent);
            box.setFillWidth(true);
            alert.getDialogPane().setContent(box);
            alert.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
            Button closeBtn = (Button) alert.getDialogPane().lookupButton(ButtonType.CLOSE);
            closeBtn.setDisable(true); // only enabled at 100%
            alert.getDialogPane().setPrefWidth(460);

            // Prevent closing before both are done
            final BooleanProperty done = new SimpleBooleanProperty(false);
            alert.setOnCloseRequest(ev -> {
                if (!done.get()) ev.consume();
            });

            // Two background tasks: stop by name/ID and mark progress at 100% on completion
            Task<Boolean> t1 = new Task<>() {
                @Override
                protected Boolean call() {
                    try {
                        // No fine-grained progress from Docker; jump to 100% when finished
                        boolean ok = DockerController.getInstance().stopContainerByNameOrId(name1, timeoutSeconds);
                        updateProgress(1, 1);
                        return ok;
                    } catch (Throwable t) {
                        updateProgress(1, 1);
                        return false;
                    }
                }
            };
            Task<Boolean> t2 = new Task<>() {
                @Override
                protected Boolean call() {
                    try {
                        boolean ok = DockerController.getInstance().stopContainerByNameOrId(name2, timeoutSeconds);
                        updateProgress(1, 1);
                        return ok;
                    } catch (Throwable t) {
                        updateProgress(1, 1);
                        return false;
                    }
                }
            };

            // Combined progress = average of task progresses
            DoubleBinding combined = t1.progressProperty().add(t2.progressProperty()).divide(2.0);
            bar.progressProperty().bind(combined);
            percent.textProperty().bind(Bindings.createStringBinding(
                    () -> Math.min(100, (int) Math.round(combined.get() * 100)) + "%",
                    combined
            ));

            // When BOTH tasks finish, finalize UI
            Runnable onAllDone = () -> {
                if (t1.isDone() && t2.isDone()) {
                    boolean ok1 = getSafe(t1, false);
                    boolean ok2 = getSafe(t2, false);
                    title.setText(ok1 && ok2 ? "Containers stopped." : "Some containers failed to stop.");
                    done.set(true);
                    closeBtn.setDisable(false);

                    // Snap to 100% in case rounding left it at 99%
                    bar.progressProperty().unbind();
                    bar.setProgress(1.0);
                    percent.textProperty().unbind();
                    percent.setText("100%");

                    // Auto-close after a short beat (optional). Remove if you want manual close only.
                    new Timeline(new KeyFrame(javafx.util.Duration.millis(900), e -> alert.close())).play();
                }
            };

            t1.stateProperty().addListener((obs, old, st) -> {
                if (st == Worker.State.SUCCEEDED || st == Worker.State.FAILED || st == Worker.State.CANCELLED) {
                    onAllDone.run();
                }
            });
            t2.stateProperty().addListener((obs, old, st) -> {
                if (st == Worker.State.SUCCEEDED || st == Worker.State.FAILED || st == Worker.State.CANCELLED) {
                    onAllDone.run();
                }
            });

            // Run tasks on a small executor (non-daemon so JVM waits if needed)
            ExecutorService pool = Executors.newFixedThreadPool(2);
            pool.submit(t1);
            pool.submit(t2);

            // Show ONE modal alert and wait until it's closed (which happens when both tasks are done)
            // Because tasks are background, the dialog remains responsive and updates progress.
            alert.showAndWait();

            // Cleanup executor
            pool.shutdownNow();
        } else
            logger.info("No running containers");
    }

    // Helper: safely unwrap Task result without throwing
    private static <T> T getSafe(Future<T> f, T fallback) {
        try {
            return f.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean showCloseConfirmation() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Exit");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Are you sure you want to close the application?");

        ButtonType yesButton = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.NO);
        confirmAlert.getButtonTypes().setAll(yesButton, noButton);

        return confirmAlert.showAndWait().filter(response -> response == yesButton).isPresent();
    }

    @Override
    public void stop() {
        this.stopBothContainersWithOneAlert(HomeController.dockerAllToolsContainer, HomeController.dockerX3DNAContainer, 10);
    }

    public static void main(String[] args) {
        launch();
    }
}