package it.unicam.cs.bdslab.tarnas.view.utils;

import it.unicam.cs.bdslab.tarnas.App;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class LenCell extends TableCell<Path, Path> {

    private final ImageButton imageButton;

    public LenCell(Image image) {
        this.imageButton = new ImageButton(image);
    }

    @Override
    protected void updateItem(Path rnaFile, boolean empty) {
        super.updateItem(rnaFile, empty);
        if (rnaFile == null) {
            setGraphic(null);
            return;
        }
        setGraphic(imageButton);

    }

}
