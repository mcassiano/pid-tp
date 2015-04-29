package me.cassiano.tp_pid;

import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.DicomFileUtilities;
import com.pixelmed.display.SourceImage;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.stage.FileChooser;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ImageProcessingController implements Initializable {


    @FXML
    private Slider zoomSlider;

    @FXML
    private Button inSeed;

    @FXML
    private Button outSeed;

    @FXML
    private Group rootGroup;

    @FXML
    private Button clearSeedsButton;

    private Group zoomGroup;

    private Image currentImage;

    private Canvas canvas;

    private DoubleProperty sliderZoomProperty = new SimpleDoubleProperty(100);

    private boolean pickingSeed = false;

    private Group internalSeed;
    private Group externalSeed;

    private Path tempPath;

    private Seed seedBeingPicked;


    private enum Seed {
        Internal, External;
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {

        zoomGroup = new Group();

        rootGroup.getChildren().add(zoomGroup);

    }


    public void openImageClicked(ActionEvent actionEvent) {

        if (currentImage == null) {
            registerScrollListener();
            registerSliderListener();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar imagem");
        File file = fileChooser.showOpenDialog(inSeed.getScene().getWindow());


        if (file != null) {

            if (DicomFileUtilities.isDicomOrAcrNemaFile(file)) {

                try {
                    SourceImage si = new SourceImage(file.getAbsolutePath());
                    BufferedImage bi = si.getBufferedImage();

                    currentImage = SwingFXUtils.toFXImage(bi, null);

                    // Ainda não consegui converter DICOM 16 bits
                    // para um objeto Mat do OpenCV, então não converto pra
                    // escala de cinza.

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (DicomException e) {
                    e.printStackTrace();
                }

            }

            else {

                Mat mat = Highgui.imread(file.getAbsolutePath());
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);
                currentImage = mat2Image(mat);

            }

            if (canvas != null)
                zoomGroup.getChildren().remove(canvas);

            clearPoints();

            canvas = new Canvas(currentImage.getWidth(), currentImage.getHeight());
            canvas.getGraphicsContext2D().drawImage(currentImage, 0, 0);

            zoomGroup.getChildren().add(canvas);

            enableSeedButtons();
        }

    }

    private void clearPoints() {

        if (internalSeed != null)
            internalSeed.getChildren().removeAll(internalSeed.getChildren());

        if (externalSeed != null)
            externalSeed.getChildren().removeAll(externalSeed.getChildren());


    }

    private void registerCanvasForMouseEvents() {

        canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {

                Group seed;
                Color strokeColor;

                if (seedBeingPicked == Seed.Internal) {

                    if (internalSeed == null) {
                        internalSeed = new Group();
                        zoomGroup.getChildren().add(internalSeed);
                    }

                    seed = internalSeed;
                    strokeColor = Color.GREEN;
                }
                else {

                    if (externalSeed == null) {
                        externalSeed = new Group();
                        zoomGroup.getChildren().add(externalSeed);
                    }

                    seed = externalSeed;
                    strokeColor = Color.BLUE;
                }

                seed.getChildren().removeAll(seed.getChildren());

                tempPath = new Path();

                tempPath.setMouseTransparent(true);
                tempPath.setStrokeWidth(3.0);
                tempPath.setStroke(strokeColor);

                seed.getChildren().add(tempPath);

                tempPath.getElements().add(
                        new MoveTo(event.getX(), event.getY()));

            }
        });

        canvas.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (canvas.getBoundsInLocal().contains(
                        event.getX(), event.getY())) {

                    tempPath.getElements().add(
                            new LineTo(event.getX(), event.getY()));
                }
            }
        });

        canvas.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {

                canvas.setOnMousePressed(null);
                canvas.setOnMouseDragged(null);

                tempPath = null;

                enableSeedButtons();

            }
        });

    }

    private void disableSeedButtons() {
        pickingSeed = true;

        zoomSlider.setDisable(true);
        inSeed.setDisable(true);
        outSeed.setDisable(true);
        clearSeedsButton.setDisable(true);
    }

    private void enableSeedButtons() {
        pickingSeed = false;

        zoomSlider.setDisable(false);
        inSeed.setDisable(false);
        outSeed.setDisable(false);
        clearSeedsButton.setDisable(false);

    }

    public void inSeedClicked(ActionEvent actionEvent) {

        seedBeingPicked = Seed.Internal;

        disableSeedButtons();
        registerCanvasForMouseEvents();

    }

    public void outSeedClicked(ActionEvent actionEvent) {

        seedBeingPicked = Seed.External;
        disableSeedButtons();
        registerCanvasForMouseEvents();
    }


    private void registerSliderListener() {

        zoomSlider.setDisable(false);

        zoomSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable,
                                Number oldValue, Number newValue) {
                sliderZoomProperty.set(newValue.doubleValue());
            }
        });

        sliderZoomProperty.addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {

                if (pickingSeed)
                    return;

                zoomGroup.setScaleX(sliderZoomProperty.get()/100);
                zoomGroup.setScaleY(sliderZoomProperty.get()/100);


            }
        });

    }

    private void registerScrollListener() {

        zoomGroup.addEventFilter(ScrollEvent.ANY, new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {

                if (pickingSeed)
                    return;

                Double  blockIncrement = zoomSlider.getBlockIncrement();

                if (event.getDeltaY() > 0 &&
                        zoomSlider.getValue() + blockIncrement <= zoomSlider.getMax()) {

                    sliderZoomProperty.set(sliderZoomProperty.get() + blockIncrement);
                    zoomSlider.setValue(zoomSlider.getValue() + blockIncrement);
                }

                else if (event.getDeltaY() < 0 &&
                        zoomSlider.getValue() - blockIncrement >= zoomSlider.getMin()) {

                    sliderZoomProperty.set(sliderZoomProperty.get() - blockIncrement);
                    zoomSlider.setValue(zoomSlider.getValue() - blockIncrement);
                }

            }
        });

    }

    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Highgui.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }


    public void clearSeeds(ActionEvent actionEvent) {

        clearPoints();

    }
}
