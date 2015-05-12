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
import javafx.scene.shape.*;
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

//    @FXML
//    private RangeSlider rangeSlider;

    private Group zoomGroup;

    private Image currentImage;

    private Canvas canvas;

    private DoubleProperty sliderZoomProperty = new SimpleDoubleProperty(100);

    private boolean pickingSeed = false;

    private Seed internalSeed;
    private Seed externalSeed;

    private Path tempPath;
    private Seed.Type seedBeingPicked;
    private Seed.Shape seedShape;

    private Shape shape;


    @Override
    public void initialize(URL location, ResourceBundle resources) {

        seedShape = Seed.Shape.Circle;
        zoomGroup = new Group();
        zoomGroup.setMouseTransparent(false);
        rootGroup.getChildren().add(zoomGroup);

    }

    public void openImageClicked(ActionEvent actionEvent) {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar imagem");
        File file = fileChooser.showOpenDialog(inSeed.getScene().getWindow());


        if (file != null) {

            if (currentImage == null) {
                registerScrollListener();
                registerSliderListener();
                //registerRangeSliderListener();
            }

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

        if (internalSeed != null) {
            internalSeed.getView().getChildren().
                    removeAll(internalSeed.getView().getChildren());
            internalSeed = null;
        }

        if (externalSeed != null) {
            externalSeed.getView().getChildren().
                    removeAll(externalSeed.getView().getChildren());
            externalSeed = null;
        }


    }

    private void registerCanvasForMouseEvents() {

        canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {

                Seed seed;
                Color strokeColor;

                if (seedBeingPicked == Seed.Type.Internal) {

                    if (internalSeed == null) {
                        internalSeed = new Seed();
                        internalSeed.setView(new Group());
                        zoomGroup.getChildren().add(internalSeed.getView());
                    }

                    seed = internalSeed;
                    strokeColor = Color.GREEN;
                }

                else {

                    if (externalSeed == null) {
                        externalSeed = new Seed();
                        externalSeed.setView(new Group());
                        zoomGroup.getChildren().add(externalSeed.getView());
                    }

                    seed = externalSeed;
                    strokeColor = Color.BLUE;
                }

                seed.getView().getChildren().
                        removeAll(seed.getView().getChildren());


                if (seedShape == Seed.Shape.Circle) {
                    shape = new Circle();
                    ((Circle) shape).setCenterX(event.getX());
                    ((Circle) shape).setCenterY(event.getY());
                }

                else {
                    shape = new Rectangle();
                    ((Rectangle) shape).setX(event.getX());
                    ((Rectangle) shape).setY(event.getY());
                }

                shape.setFill(Color.TRANSPARENT);
                shape.setStroke(strokeColor);
                shape.setStrokeWidth(3.0);

                seed.getView().setMouseTransparent(true);
                seed.getView().getChildren().add(shape);

                tempPath = new Path();

                tempPath.setMouseTransparent(true);
                tempPath.setStrokeWidth(0.0);
                tempPath.setStroke(strokeColor);

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

                    MoveTo p = (MoveTo) tempPath.getElements().get(0);

                    Double xIn = p.getX();
                    Double yIn = p.getY();

                    if (seedShape == Seed.Shape.Circle) {

                        Circle circle = (Circle) shape;
                        circle.setCenterX(event.getX());
                        circle.setCenterY(event.getY());

                        Double deltaX = xIn - event.getX();
                        Double deltaY = yIn - event.getY();
                        Double diameter = Math.sqrt(deltaX*deltaX + deltaY*deltaY);

                        circle.setRadius(diameter/2.0);
                    }

                    else if (seedShape == Seed.Shape.Square) {

                        Rectangle rectangle = (Rectangle) shape;
                        rectangle.setWidth(Math.abs(event.getX() - xIn));
                        rectangle.setHeight(Math.abs(event.getY() - yIn));

                    }

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

        seedBeingPicked = Seed.Type.Internal;

        disableSeedButtons();
        registerCanvasForMouseEvents();

    }

    public void outSeedClicked(ActionEvent actionEvent) {

        seedBeingPicked = Seed.Type.External;
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

//    private void registerRangeSliderListener() {
//
//        rangeSlider.setDisable(false);
//
//        rangeSlider.lowValueProperty().addListener(new ChangeListener<Number>() {
//            @Override
//            public void changed(ObservableValue<? extends Number> observable,
//                                Number oldValue, Number newValue) {
//
//
//            }
//        });
//
//        rangeSlider.highValueProperty().addListener(new ChangeListener<Number>() {
//            @Override
//            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
//
//
//            }
//        });
//
//    }

    private void redrawCanvas() {

        zoomGroup.getChildren().removeAll(zoomGroup.getChildren());

        Canvas canvas = new Canvas(currentImage.getWidth(), currentImage.getHeight());
        canvas.getGraphicsContext2D().drawImage(currentImage, 0, 0);

        zoomGroup.getChildren().add(canvas);

        if (internalSeed != null)
            zoomGroup.getChildren().add(internalSeed.getView());
        if (externalSeed != null)
            zoomGroup.getChildren().add(externalSeed.getView());

    }

    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Highgui.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }


    public void clearSeeds(ActionEvent actionEvent) {

        clearPoints();

    }

    public void selectSeedShape(ActionEvent actionEvent) {

        Button button = (Button) actionEvent.getSource();

        if (button.getId().equals("circle"))
            seedShape = Seed.Shape.Circle;

        else if (button.getId().equals("square"))
            seedShape = Seed.Shape.Square;

    }
}
