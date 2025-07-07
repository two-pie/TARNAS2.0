package it.unicam.cs.bdslab.tarnas.view;

import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.controller.IOController;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.*;

import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.logging.Logger;


public class HomeController {
    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.view.HomeController");

    private final String dockerImageName = "tarnas2.0";

    private IOController ioController;
    private DockerController dockerController;

    @FXML
    private TableView<Path> filesTable;

    @FXML
    private TableColumn<Path, String> nameColumn;

    @FXML
    private TableColumn<Path, Void> deleteColumn;

    @FXML
    public MenuButton btnSelectFormatTranslation;

    @FXML
    public CheckBox chbxSaveAsZIP;

    @FXML
    public TextField textFieldArchiveName;

    @FXML
    public BorderPane paneTranslationCleaning;

    @FXML
    public BorderPane abstractionsPane;
    ;

    @FXML
    public void initialize() {
        logger.info("Initializing...");
        this.ioController = IOController.getInstance();
        this.dockerController = DockerController.getInstance();
        logger.info("Initialization done");
    }

    @FXML
    public void handleAddFile() {
        logger.info("ADD FILE button clicked");

        logger.info("Exit add file");
    }

    @FXML
    public void handleAddFolder() {
        logger.info("ADD FOLDER button clicked");
        var directoryChooser = new DirectoryChooser();
        var selectedDirectory = directoryChooser.showDialog(this.getPrimaryStage());
        if (selectedDirectory != null) {
            try {
                var sharedDirectory = selectedDirectory.toPath();
                this.ioController.loadDirectory(sharedDirectory);
                this.dockerController.init(this.dockerImageName, sharedDirectory);
                this.dockerController.rnaView();
                logger.info("Folder added successfully");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "", "", e.getMessage());
            }
        }
        logger.info("Exit add file");
    }


    @FXML
    public void handleReset() {
        logger.info("RESET button clicked");
        logger.info("Reset done");
    }

    @FXML
    public void handleRun() {
        logger.info("RUN button clicked");
    }


    private Stage getPrimaryStage() {
        return (Stage) this.filesTable.getScene().getWindow();
    }

    private void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Alert alert = new Alert(alertType);
        alert.initOwner(this.getPrimaryStage());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
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

    /**
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
            webView.setPrefWidth(newVal.doubleValue() - 50);  // Adjust width
        });

        alertDialog.heightProperty().addListener((obs, oldVal, newVal) -> {
            webView.setPrefHeight(newVal.doubleValue() - 100);  // Adjust height
        });

        // Intercept navigation requests and open them in the system's default browser
        webEngine.locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation != null && newLocation.startsWith("http")) {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(newLocation));  // Open the URL in the default system browser
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                webEngine.loadContent(htmlContent);  // Prevent navigation in the WebView by reloading the original content
            }
        });

        // Set the WebView as the content of the dialog
        alertDialog.getDialogPane().setContent(webView);

        // Show the dialog
        alertDialog.showAndWait();
    }
}
