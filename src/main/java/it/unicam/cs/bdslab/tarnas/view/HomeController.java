package it.unicam.cs.bdslab.tarnas.view;

import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.controller.ExtendedBPSEQExportController;
import it.unicam.cs.bdslab.tarnas.controller.IOController;
import it.unicam.cs.bdslab.tarnas.models.StructureInfo;
import it.unicam.cs.bdslab.tarnas.models.StructureStatus;
import it.unicam.cs.bdslab.tarnas.parser.output.RNASecondaryStrucutrePrinter;
import it.unicam.cs.bdslab.tarnas.view.utils.TOOL;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javafx.stage.FileChooser;

import static it.unicam.cs.bdslab.tarnas.view.utils.TOOL.*;

public class HomeController {
    public static final Logger logger = Logger.getLogger(HomeController.class.getName());

    public static final String dockerAllToolsImage = "tarnas2.0-image";
    public static final String dockerAllToolsImageTag = "latest";
    public static final String dockerAllToolsContainer = "tarnas2.0-container";
    public static final String dockerfileAllToolsPath = "./docker/all-tools";
    public static final String dockerX3DNAImage = "x3dna-image";
    public static final String dockerX3DNAImageTag = "latest";
    public static final String dockerX3DNAContainer = "x3dna-container";
    public static final String dockerfileX3DNAPath = "./docker/x3dna-tool/Dockerfile";

    private IOController ioController;
    private DockerController dockerController;
    private ExtendedBPSEQExportController extendedBPSEQExportController;

    @FXML
    private TableView<StructureInfo> filesTable;

    @FXML
    private TableColumn<StructureInfo, String> nameColumn;
    @FXML
    private TableColumn<StructureInfo, String> chainColumn;
    @FXML
    private TableColumn<StructureInfo, Void> deleteColumn;
    @FXML
    private TableColumn<StructureInfo, String> statusColumn;
    @FXML
    private TableColumn<StructureInfo, String> errorColumn;

    @FXML
    public BorderPane paneTranslationCleaning;

    @FXML
    public BorderPane abstractionsPane;

    @FXML
    public ListView<TOOL> toolListView;

    @FXML
    private Button btn_run;

    @FXML
    private Button btn_addCsv;

    @FXML
    private Label label_folder;

    @FXML
    private CheckBox ck_extractSS;

    @FXML
    private CheckBox ck_extractESS;

    @FXML
    private CheckBox ck_consensus;

    @FXML
    private ChoiceBox<RNASecondaryStrucutrePrinter.OutputFormat> select_outputSS;

    @FXML
    private ChoiceBox<RNASecondaryStrucutrePrinter.OutputFormat> select_outputESS;

    private Map<TOOL, BooleanProperty> checkedItems = new HashMap<>();
    private boolean x3dnaAvailable = true;

    private final ObservableList<StructureInfo> structures = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        logger.info("Initializing...");
        this.ioController = IOController.getInstance();
        this.dockerController = DockerController.getInstance();
        this.extendedBPSEQExportController = ExtendedBPSEQExportController.getInstance();

        if (this.ioController.getSharedDirectory() != null) {
            String folderText = "Folder: " + this.ioController.getSharedDirectory();
            label_folder.setText(folderText);
            label_folder.setTooltip(new Tooltip(folderText));
        }

        refreshToolListAvailability();

        toolListView.setCellFactory(CheckBoxListCell
                .forListView(tool -> checkedItems.computeIfAbsent(tool, t -> new SimpleBooleanProperty(false))));

        // Set to enable or disable the "Run" button based on the selection of tools
        toolListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        toolListView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            boolean anySelected = checkedItems.values().stream().anyMatch(BooleanProperty::get);
            btn_run.setDisable(!anySelected);
        });

        this.filesTable.setItems(this.structures);

        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));

        chainColumn.setCellValueFactory(x -> new ReadOnlyStringWrapper(x.getValue().getChain()));

        statusColumn.setCellValueFactory(x -> new ReadOnlyStringWrapper(x.getValue().getStatus().translate()));

        deleteColumn.setCellFactory(col -> new TableCell<StructureInfo, Void>() {
            private final Button btn = new Button();

            {
                Image trashImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/trash.png")));
                ImageView trashIcon = new ImageView(trashImage);
                trashIcon.setFitWidth(16);
                trashIcon.setFitHeight(16);
                trashIcon.setPreserveRatio(true);

                btn.setGraphic(trashIcon);
                btn.setText("Delete");
                btn.setContentDisplay(ContentDisplay.LEFT);
                btn.setOnAction(e -> {
                    StructureInfo item = getTableView().getItems().get(getIndex());
                    filesTable.getItems().remove(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });

        btn_run.setDisable(true);

        handleExtractSelected(ck_extractESS);

        handleExtractSelected(ck_extractSS);

        btn_addCsv.setOnAction((actionEvent) -> {
            handleAddMoleculesList();
        });

        select_outputSS.setItems(FXCollections.observableArrayList(
                RNASecondaryStrucutrePrinter.OutputFormat.getNonExtendedFormats()));

        select_outputESS.setItems(FXCollections.observableArrayList(
                RNASecondaryStrucutrePrinter.OutputFormat.getExtendedFormats()));

        select_outputSS.setValue(RNASecondaryStrucutrePrinter.OutputFormat.BPSEQ);
        select_outputESS.setValue(RNASecondaryStrucutrePrinter.OutputFormat.EXTENDED_BPSEQ);

        logger.info("Initialization done");
    }

    private void handleExtractSelected(CheckBox ckExtractX) {
        ckExtractX.setOnAction((actionEvent) -> {
            actionEvent.consume();
            boolean selected = ckExtractX.isSelected();
            boolean noToolSelected = checkedItems.values().stream().noneMatch(BooleanProperty::get);
            if (selected && !noToolSelected) {
                btn_run.setDisable(false);
            } else {
                if (!ck_extractSS.isSelected() && !ck_extractESS.isSelected() && !ck_consensus.isSelected()) {
                    btn_run.setDisable(true);
                }
            }
        });
    }

    @FXML
    public void handleAddFile() {
        logger.info("ADD FILE button clicked");

        logger.info("Exit add file");
    }

    @FXML
    public void handleAddMoleculesList() {
        logger.info("ADD CSV MOLECULES LIST button clicked");

        if (ioController.getSharedDirectory() == null) {
            showAlert(Alert.AlertType.WARNING, "No Folder Selected", "", "Complete setup first to initialize Docker containers.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CSV molecules list");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));

        Path currentDirectory = ioController.getSharedDirectory();
        if (currentDirectory != null && currentDirectory.toFile().isDirectory()) {
            fileChooser.setInitialDirectory(currentDirectory.toFile());
        }

        File selectedCsv = fileChooser.showOpenDialog(this.getPrimaryStage());
        if (selectedCsv == null) {
            logger.info("No CSV selected");
            return;
        }

        try {
            Path csvPath = selectedCsv.toPath();
            Path sharedDirectory = csvPath.getParent();

            if (!sharedDirectory.equals(ioController.getSharedDirectory())) {
                showAlert(Alert.AlertType.WARNING, "Wrong CSV Folder", "",
                        "Select a CSV inside the configured shared folder: " + ioController.getSharedDirectory());
                return;
            }

            String folderText = "Folder: " + sharedDirectory;
            label_folder.setText(folderText);
            label_folder.setTooltip(new Tooltip(folderText));

            Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
            loadingAlert.setTitle("Processing CSV");
            loadingAlert.setHeaderText(null);
            
            ProgressBar bar = new ProgressBar();
            bar.setPrefWidth(380);
            VBox box = new VBox(10, new Label("Loading molecules from CSV..."), bar);
            loadingAlert.getDialogPane().setContent(box);
            loadingAlert.getDialogPane().getButtonTypes().clear();

            Task<List<StructureInfo>> preprocessTask = new Task<>() {
                @Override
                protected List<StructureInfo> call() throws Exception {
                    return dockerController.preprocessCsvAndCollectStructures(sharedDirectory, csvPath);
                }
            };

            preprocessTask.setOnSucceeded(ev -> {
                loadingAlert.setResult(ButtonType.OK);
                loadingAlert.close();
                List<StructureInfo> generatedStructures = preprocessTask.getValue();
                structures.setAll(generatedStructures);
                logger.info("Loaded " + generatedStructures.size() + " molecules from preprocessed output");
            });

            preprocessTask.setOnFailed(ev -> {
                loadingAlert.setResult(ButtonType.OK);
                loadingAlert.close();
                showAlert(Alert.AlertType.ERROR, "CSV preprocessing error", "",
                        Optional.ofNullable(preprocessTask.getException())
                                .map(Throwable::getMessage)
                                .orElse("Unknown preprocessing error"));
            });

            new Thread(preprocessTask, "csv-preprocess").start();
            loadingAlert.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "CSV load error", "", e.getMessage());
        }

        logger.info("Exit add molecules list");
    }

    @FXML
    public void handleReset() {
        logger.info("RESET button clicked");
        logger.info("Reset done");
    }

    @FXML
    public void handleRun() throws InterruptedException, IOException {
        logger.info("RUN button clicked");
        refreshToolListAvailability();

        List<TOOL> selectedTools = checkedItems.entrySet()
                .stream()
                .filter(e -> e.getValue().get())
                .map(Map.Entry::getKey)
                .toList();

        if (!x3dnaAvailable) {
            selectedTools = selectedTools.stream().filter(t -> t != X3DNA).toList();
        }

        logger.info("Selected tools: " + selectedTools);
        if (selectedTools.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Tool Selected", "", "Please select at least one tool to run.");
            logger.warning("No tool selected to run.");
            return;
        }

        if (this.ioController.getSharedDirectory() == null) {
            showAlert(Alert.AlertType.WARNING, "No Folder Selected", "", "Please select a folder to run the tools.");
            logger.warning("No folder selected to run the tools.");
            return;
        }

        this.executeCommand(new HashSet<>(selectedTools), true);
    }

    private Stage getPrimaryStage() {
        return (Stage) this.filesTable.getScene().getWindow();
    }

    public void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Alert alert = new Alert(alertType);
        alert.initOwner(getPrimaryStage());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * This method can be used to execute the pipeline:
     * 1. Execute the selected tools (e.g., RNAPolis Annotator, RNAView...)
     * 2. Generate extended BPSEQ or normal BPSEQ files based on the output of the
     * tools and the user's choice.
     * 
     * @param selectedTools       the set of tools selected by the user to run
     * @param outputExtendedBPSEQ if the output format should be extended BPSEQ
     *                            (true) or normal BPSEQ (false)
     */
    private void executeCommand(Set<TOOL> selectedTools, boolean outputExtendedBPSEQ) {

        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle("Processing");
        loadingAlert.setHeaderText(null);

        Label title = new Label("Running selected tools…");
        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(380);
        Label percent = new Label("0%");

        VBox box = new VBox(10, title, bar, percent);
        loadingAlert.getDialogPane().setContent(box);
        loadingAlert.getDialogPane().setPrefWidth(460);
        loadingAlert.getDialogPane().getButtonTypes().clear();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0, selectedTools.size());
                int total = selectedTools.size();
                int count = 0;
                Map<String, String> supportSequences = Map.of();

                if (selectedTools.stream().anyMatch(Predicate.not(TOOL::giveStructure))) {
                    actionsMap.get(RNAPOLIS_ANNOTATOR).run();
                    supportSequences = extendedBPSEQExportController.loadStructures(TOOL.RNAPOLIS_ANNOTATOR, ioController.getSharedDirectory())
                            .stream()
                            .map(e -> Map.entry(e.baseName(), e.structure().getSequence()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }

                for (TOOL tool : selectedTools) {

                    updateMessage(tool.getName());

                    Runnable action = actionsMap.get(tool);
                    if (action != null) {
                        action.run();
                    }

                    extendedBPSEQExportController.exportForTool(
                            tool,
                            ioController.getSharedDirectory(),
                            ck_extractSS.isSelected()
                                    ? RNASecondaryStrucutrePrinter.OutputFormat.BPSEQ
                                    : null,
                            ck_extractESS.isSelected()
                                    ? RNASecondaryStrucutrePrinter.OutputFormat.EXTENDED_BPSEQ
                                    : null,
                            supportSequences
                            );

                    count++;
                    updateProgress(count, total);
                }

                return null;
            }
        };

        // Bind UI
        bar.progressProperty().bind(task.progressProperty());
        percent.textProperty().bind(
                task.progressProperty().multiply(100).asString("%.0f%%"));

        title.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            this.filesTable.getItems()
                            .forEach(s -> s.setStatus(StructureStatus.PROCESSED));
            this.filesTable.refresh();
            loadingAlert.setResult(ButtonType.OK);
            loadingAlert.close();

            javafx.application.Platform.runLater(() -> {
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "Process Completed",
                        "",
                        "Selected tools have been executed and output files are saved in: "
                                + ioController.getSharedDirectory());
            });
        });

        task.setOnFailed(e -> {
            loadingAlert.setResult(ButtonType.OK);
            loadingAlert.close();
            javafx.application.Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Process Error", "", 
                    "An error occurred during execution: " + task.getException().getMessage());
            });
        });

        loadingAlert.show();
        new Thread(task).start();
    }

    private Map<TOOL, Runnable> actionsMap = Map.of(
            RNAPOLIS_ANNOTATOR, () -> {
                try {
                    this.dockerController.rnapolisAnnotator();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            RNAVIEW, () -> {
                try {
                    this.dockerController.rnaView();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            BARNABA, () -> {
                try {
                    this.dockerController.baRNAba();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            BPNET, () -> {
                try {
                    this.dockerController.bpnet();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            FR3D, () -> {
                try {
                    this.dockerController.fr3d();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            X3DNA, () -> {
                try {
                    this.dockerController.x3dnaBy(dockerX3DNAContainer);
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            },
            MC_ANNOTATE, () -> {
                try {
                    this.dockerController.mcAnnotate();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

    private void refreshToolListAvailability() {
        boolean imageAvailable = dockerController.dockerImageExists(dockerX3DNAImage);
        boolean zipAvailable = dockerController.isX3DNABuildContextAvailable(new File(dockerfileX3DNAPath));
        this.x3dnaAvailable = imageAvailable || zipAvailable;

        ObservableList<TOOL> tools = FXCollections.observableArrayList(TOOL.values());
        if (!x3dnaAvailable) {
            tools.remove(X3DNA);
            BooleanProperty selected = checkedItems.get(X3DNA);
            if (selected != null) {
                selected.set(false);
            }
            logger.warning("X3DNA hidden: missing image and required archive dssr-basic-linux-v2.8.1.zip in docker/x3dna-tool.");
        }
        toolListView.setItems(tools);
    }

    /**
     * 
     * General method to display a resizable Alert dialog with HTML content.
     *
     * @param title       The title of the Alert dialog.
     * @param header      The header text of the Alert dialog.
     * @param htmlContent The HTML content to display inside the WebView.
     */
    private void showAlertWithContent(String title, String header, String htmlContent) {
        Alert alertDialog = new Alert(Alert.AlertType.INFORMATION);
        alertDialog.setTitle(title);
        alertDialog.setHeaderText(header);

        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.loadContent(htmlContent);

        // Set initial size for the WebView
        webView.setPrefSize(1000, 600);

        // Allow resizing of the alert dialog
        alertDialog.setResizable(true);

        // Adjust WebView size when the dialog is resized
        alertDialog.widthProperty().addListener((obs, oldVal, newVal) -> {
            webView.setPrefWidth(newVal.doubleValue() - 50); // Adjust width
        });

        alertDialog.heightProperty().addListener((obs, oldVal, newVal) -> {
            webView.setPrefHeight(newVal.doubleValue() - 100); // Adjust height
        });

        // Intercept navigation requests and open them in the system's default browser
        webEngine.locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation != null && newLocation.startsWith("http")) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(newLocation)); // Open the URL in the default system browser
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                webEngine.loadContent(htmlContent); // Prevent navigation in the WebView by reloading the original
                                                    // content
            }
        });

        // Set the WebView as the content of the dialog
        alertDialog.getDialogPane().setContent(webView);

        // Show the dialog
        alertDialog.showAndWait();
    }

    @FXML
    public void handleHelp() {
        String helpContent = """
                            <h2>General information</h2>
                               EXTRARNAS (Extract RNA Structures) is a Java application designed to analyze RNA 3D structures and extract their secondary structures and base-pairing interactions. It provides a guided interface to configure and run specialized bioinformatics tools inside isolated Docker environments, generating standardized outputs.
                               <br><br>
                               <h2>How to use EXTRARNAS</h2>
                                       There are a few main steps for using the EXTRARNAS application:
                                       <ol>
                                           <li>Workspace Setup: select the local directory to share with the Docker container for passing inputs and retrieving outputs.</li>
                                           <li>Tool Selection: choose the structural analysis tool you want to launch.</li>
                                           <li>Structure Analysis Level: choose between Secondary Structure (classical pairings) or Extended Secondary Structure (non-canonical interactions).</li>
                                           <li>Output Formats: Output supports BPSEQ (standard canonical) and Extended BPSEQ (including all bounds).</li>
                                       </ol>
                """;

        showAlertWithContent("Help", "How to Use This Application", helpContent);
    }

    @FXML
    public void handleAbout() {
        String aboutContent = """
                        <h2>About EXTRARNAS</h2>EXTRARNAS is a tool that analyzes RNA 3D structures and extracts their secondary structures using specialized bioinformatics tools in containerized environments.
                              <br>
                              It allows users to extract secondary structures and base-pairing interactions, ranging from canonical A-U, G-C, G-U pairs to fully extended non-canonical bounds.
                              <br>EXTRARNAS is developed as a standalone desktop application.
                              <br>It currently leverages Docker to execute tools like x3dna-dssr and others in isolated, perfectly reproducible environments.
                             <br><br>
                        <h2>Citations</h2>
                        Any published work that has made use of EXTRARNAS may cite the following paper:
                            <br><br>
                            EXTRARNAS, a tool for RNA Structures Extraction.
                        <br><br>
                        <h2>Acknowledgements and Funding</h2><em>This work was supported by the European Union - Next-Generation EU - National Recovery and
                            Resilience Plan (NRRP) - MISSION 4 COMPONENT 2, INVESTMENT N. 1.1, CALL PRIN 2022
                            PNRR D.D. 1409 of 14th Sep 2022 - RNA2FUN CUP N. J53D23014960001- RNA2Fun:
                            <a href="https://bdslab.unicam.it/rna2fun/" target="_blank">https://bdslab.unicam.it/rna2fun/</a></em>
                """;

        showAlertWithContent("About EXTRARNAS", "About This Application", aboutContent);
    }

    @FXML
    public void handleContactUs() {
        String contactUsContent = """
                <h2>Contact Us</h2>
                        <b class="bigger_text">EXTRARNAS has been realised within the <a href="http://www.emanuelamerelli.eu/bigdata/doku.php" target="_blank">BioShape and Data Science Lab</a> with the contribution of Piero Jean Pier Hierro Canchari, Michela Quadrini, Piermichele Rosati, Di Petta Federico and Luca Tesei.</b>
                        <p>Lab website: <a href="https://bdslab.unicam.it" target="_blank">https://bdslab.unicam.it</a></p>

                        <p>RNA2Fun Project website: <a href="https://bdslab.unicam.it/rna2fun/" target="_blank">https://bdslab.unicam.it/rna2fun/</a></p>

                        <b class="bigger_text">For any issue, please contact:</b>
                        <p>Prof. Luca Tesei</p>
                        <p>email: luca.tesei&#64;unicam.it</p>

                        <p>address: School of Sciences and Technology, Via Madonna delle Carceri 7, 62032, Camerino (MC), Italy</p>

                        <p>Personal website: <a href="http://www.lucatesei.com" target="_blank">http://www.lucatesei.com</a></p>
                """;
        showAlertWithContent("About EXTRARNAS", "Contact Us", contactUsContent);
    }
}
