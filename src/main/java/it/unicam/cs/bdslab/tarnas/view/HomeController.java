package it.unicam.cs.bdslab.tarnas.view;

import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.controller.ExtendedBPSEQExportController;
import it.unicam.cs.bdslab.tarnas.controller.IOController;
import it.unicam.cs.bdslab.tarnas.models.StructureInfo;
import it.unicam.cs.bdslab.tarnas.parser.output.RNASecondaryStrucutrePrinter;
import it.unicam.cs.bdslab.tarnas.view.utils.TOOL;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
import java.util.List;
import java.util.logging.Logger;

import javafx.stage.FileChooser;

import static it.unicam.cs.bdslab.tarnas.view.utils.TOOL.*;

public class HomeController {
    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.view.HomeController");

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
            label_folder.setText("Folder: " + this.ioController.getSharedDirectory());
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

            label_folder.setText("Folder: " + sharedDirectory);
            Task<List<StructureInfo>> preprocessTask = new Task<>() {
                @Override
                protected List<StructureInfo> call() throws Exception {
                    return dockerController.preprocessCsvAndCollectStructures(sharedDirectory, csvPath);
                }
            };

            preprocessTask.setOnSucceeded(ev -> {
                List<StructureInfo> generatedStructures = preprocessTask.getValue();
                structures.setAll(generatedStructures);
                logger.info("Loaded " + generatedStructures.size() + " molecules from preprocessed output");
            });

            preprocessTask.setOnFailed(ev ->
                    showAlert(Alert.AlertType.ERROR, "CSV preprocessing error", "",
                            Optional.ofNullable(preprocessTask.getException())
                                    .map(Throwable::getMessage)
                                    .orElse("Unknown preprocessing error")));

            new Thread(preprocessTask, "csv-preprocess").start();
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

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0, selectedTools.size());
                int total = selectedTools.size();
                int count = 0;

                for (TOOL tool : selectedTools) {

                    updateMessage(tool.getName());

                    Runnable action = actionsMap.get(tool);
                    if (action != null) {
                        action.run();
                    }

                    extendedBPSEQExportController.exportForTool(
                            tool,
                            ioController.getSharedDirectory(),
                            ck_extractSS.isSelected() ? RNASecondaryStrucutrePrinter.OutputFormat.BPSEQ : null,
                            ck_extractESS.isSelected() ? RNASecondaryStrucutrePrinter.OutputFormat.EXTENDED_BPSEQ
                                    : null);

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
            loadingAlert.close();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Process Completed",
                    "",
                    "Selected tools have been executed and output files are saved in: "
                            + ioController.getSharedDirectory());
        });

        new Thread(task).start();
        loadingAlert.show();
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
                               RNA secondary structure analysis, including comparison and classification, plays a fundamental
                                       role in facing different problems, such as the prediction of RNA functions and the study of
                                       regulating gene expression. Existing tools for RNA analysis do not take secondary structures as
                                       input in the same formats due to the lack of an input standard to represent RNA secondary
                                       structures.
                                       <br>TARNAS supports translations of RNA secondary structures in the following formats:
                                       <ul>
                                       <li>BPSEQ - <a href="https://www.ibi.vu.nl/programs/k2nwww/static/data_formats.html" target="_blank">https://www.ibi.vu.nl/programs/k2nwww/static/data_formats.html</a></li>
                                       <li>CT - <a href="https://www.ibi.vu.nl/programs/k2nwww/static/data_formats.html" target="_blank">https://www.ibi.vu.nl/programs/k2nwww/static/data_formats.html</a></li>
                                       <li>Dot-Bracket - <a href="https://www.ibi.vu.nl/programs/k2nwww/static/data_formats.html" target="_blank">https://www.ibi.vu.nl/programs/k2nwww/static/data_formats.html</a></li>
                                       <li>RNAML - <a href="https://pmc.ncbi.nlm.nih.gov/articles/PMC1370290/" target="_blank">https://pmc.ncbi.nlm.nih.gov/articles/PMC1370290/</a></li>
                                       <li>arc-annotated sequence - <a href="https://github.com/bdslab/aspralign" target="_blank">https://github.com/bdslab/aspralign</a></li>
                                       <li>Fasta (only primary structure) - <a href="https://www.ncbi.nlm.nih.gov/genbank/fastaformat/" target="_blank">https://www.ncbi.nlm.nih.gov/genbank/fastaformat/</a></li>
                                       </ul>
                                       <br>
                                       Moreover, TARNAS allows the user to abstract the RNA secondary structure into three forms, i.e.,
                                       Core, Core Plus and Shape, and to edit or delete the header of files.
                               <br><br>
                               <h2>How to use TARNAS</h2>
                                       There are three main scenarios for using the TARNAS application:
                                       <ol>
                                           <li>RNA secondary structure translations</li>
                                           <li>Deleting or retaining comments, blank lines and headers of the file</li>
                                           <li>Abstracting RNA secondary structures into three views: Core, Core Plus and Shape</li>
                                       </ol>
                                       <em>RNA secondary structure translations scenario.</em>
                                       <br><br>
                                       Step 1. In the first step of this scenario, a user should upload the RNA secondary structure provided
                                       in a supported format.
                                       <br>The file can be uploaded directly from a local drive (use the &quot;Browse&quot; button
                                       to browse through the local repositories).
                                       <br>There are two examples stored in the system and ready for
                                       processing. Uploaded data can be viewed in the text area after clicking the &quot;Preview&quot; button and
                                       edited before further processing.
                                       <br><br>
                                       Step 2. In this step, the user can decide whether to include or not the header and the output format.
                                       <br>The user selects the additional option &quot;include reader&quot; to include the header and the format in the
                                       dropdown menu.
                                       <br><br>
                                       Step 3. To start the transformation of secondary structure, the &quot;Run&quot; button should be clicked.
                                       <br><br>
                                       <em>Deleting or retaining comments, blank lines and headers of the file.</em>
                                       <br><br>
                                       Step 1. In the first step of this scenario, a user should upload the RNA secondary structure provided
                                       in a supported format.
                                       <br>The file can be uploaded directly from a local drive (use the &quot;Browse&quot; button
                                       to browse through the local repositories).
                                       <br>There are two examples stored in the system and ready for
                                       processing. Uploaded data can be viewed in the text area after clicking the &quot;Preview&quot; button and
                                       edited before further processing.
                                       <br><br>
                                       Step 2. In this step, the user can decide to remove all comments, lines containing a particular word
                                       or empty lines by selecting the relative option.
                                       <br>If the user intends to delete lines containing a particular word, it is necessary to specify the word in the box.
                                       <br><br>
                                       Step 3. To start editing or delete the comments, the &quot;Run&quot; button should be clicked.
                                       <br><br>
                                       <em>Abstracting RNA secondary structures into three views: Core, Core Plus and Shape</em>
                                       <br><br>
                                       Step 1. In the first step of this scenario, a user should upload the RNA secondary structure provided
                                       in a supported format.
                                       <br>The file can be uploaded directly from a local drive (use the &quot;Browse&quot; button
                                       to browse through the local repositories).
                                       <br>There are two examples stored in the system and ready for
                                       processing. Uploaded data can be viewed in the text area after clicking the &quot;Preview&quot; button and
                                       edited before further processing.
                                       <br><br>
                                       Step 2. In this step, the user can decide the type of abstractions, such as Core, Core Plus, or Shape by selecting the corresponding option.
                                       <br><br>
                                       Step 3. To start editing or delete the comments, the &quot;Run&quot; button should be clicked.
                """;

        showAlertWithContent("Help", "How to Use This Application", helpContent);
    }

    @FXML
    public void handleAbout() {
        String aboutContent = """
                        <h2>About TARNAS</h2>TARNAS is a tool that translates RNA secondary structures into different formats, including
                              BPSEQ, CT, RNAML, Dot-Bracket, FASTA (only primary structure) and arc-annotated sequence.
                              <br>
                              Moreover, TARNAS allows us to abstract RNA secondary structures into three views, namely Core,
                              Core Plus and Shape.
                              <br>Finally, TARNAS permits to delete or retain comments, blank lines and
                              headers of the files.
                              <br>TARNAS is developed as a standalone desktop application and as a web app.
                              <br>The standalone desktop application can be found at <a href="https://github.com/bdslab/TARNAS" target="_blank">https://github.com/bdslab/TARNAS</a> and the
                              web app is at <a href="https://bdslab.unicam.it/tarnas/" target="_blank">https://bdslab.unicam.it/tarnas/</a>
                             <br><br>
                        <h2>Citations</h2>
                        Any published work that has made use of TARNAS may cite the following paper:
                            <br><br>
                            Michela Quadrini, Piero Hierro Canchari, Piermichele Rosati, and Luca Tesei, TARNAS, a
                            TrAnslator for RNA Secondary structure formats.
                        <br><br>
                        <h2>Acknowledgements and Funding</h2><em>This work was supported by the European Union - Next-Generation EU - National Recovery and
                            Resilience Plan (NRRP) - MISSION 4 COMPONENT 2, INVESTMENT N. 1.1, CALL PRIN 2022
                            PNRR D.D. 1409 of 14th Sep 2022 - RNA2FUN CUP N. J53D23014960001- RNA2Fun:
                            <a href="https://bdslab.unicam.it/rna2fun/" target="_blank">https://bdslab.unicam.it/rna2fun/</a></em>
                """;

        showAlertWithContent("About TARNAS", "About This Application", aboutContent);
    }

    @FXML
    public void handleContactUs() {
        String contactUsContent = """
                <h2>Contact Us</h2>
                        <b class="bigger_text">TARNAS has been realised within the <a href="http://www.emanuelamerelli.eu/bigdata/doku.php" target="_blank">BioShape and Data Science Lab</a> with the contribution of Piero Jean Pier Hierro Canchari, Michela Quadrini, Piermichele Rosati and Luca Tesei.</b>
                        <p>Lab website: <a href="https://bdslab.unicam.it" target="_blank">https://bdslab.unicam.it</a></p>

                        <p>RNA2Fun Project website: <a href="https://bdslab.unicam.it/rna2fun/" target="_blank">https://bdslab.unicam.it/rna2fun/</a></p>

                        <b class="bigger_text">For any issue, please contact:</b>
                        <p>Prof. Luca Tesei</p>
                        <p>email: luca.tesei&#64;unicam.it</p>

                        <p>address: School of Sciences and Technology, Via Madonna delle Carceri 7, 62032, Camerino (MC), Italy</p>

                        <p>Personal website: <a href="http://www.lucatesei.com" target="_blank">http://www.lucatesei.com</a></p>
                """;
        showAlertWithContent("About TARNAS", "Contact Us", contactUsContent);
    }
}
