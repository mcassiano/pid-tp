package me.cassiano.tp_pid;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.io.Opener;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
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
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.FileChooser;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;
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
    private Button runRegionGrowingButton;

    @FXML
    private Group rootGroup;

    @FXML
    private Button clearSeedsButton;

    @FXML
    private Slider minSlider;

    @FXML
    private Slider maxSlider;

    @FXML
    private BarChart<String, Number> histogramChart;

    private Group zoomGroup;

    private ImagePlus originalImage;

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

            if (originalImage == null) {
                registerScrollListener();
                registerListenerForZoomSlider();
                registerListenerForWindowingSliders();
            }

            originalImage = new Opener().openImage(file.getPath());
            ImageConverter converter = new ImageConverter(originalImage);
            converter.convertToGray8();

            if (canvas == null) {
                canvas = new Canvas(originalImage.getWidth(), originalImage.getHeight());
                zoomGroup.getChildren().add(canvas);
            }

            clearPoints();

            resetWindowingSliders();

            redrawCanvas();

            showHistogramChart();

            enableSeedButtons();
        }

    }

    private void showHistogramChart() {

        ImageProcessor imageProcessor = originalImage.getChannelProcessor();

        int[] histogram = imageProcessor.getHistogram();

        XYChart.Series<String, Number> series1 = new XYChart.Series<String, Number>();


        for (Integer i = 0; i < histogram.length; i++) {
            XYChart.Data<String, Number> point =
                    new XYChart.Data<String, Number>(i.toString(), histogram[i]);

            series1.getData().add(point);
        }

        histogramChart.getData().clear();
        histogramChart.getData().add(series1);


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
                        internalSeed.setType(Seed.Type.Internal);
                        internalSeed.setView(new Group());
                        zoomGroup.getChildren().add(internalSeed.getView());
                    }

                    internalSeed.setShape(seedShape);
                    seed = internalSeed;
                    strokeColor = Color.GREEN;
                }

                else {

                    if (externalSeed == null) {
                        externalSeed = new Seed();
                        externalSeed.setType(Seed.Type.Internal);
                        externalSeed.setView(new Group());
                        zoomGroup.getChildren().add(externalSeed.getView());
                    }

                    externalSeed.setShape(seedShape);
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
        runRegionGrowingButton.setDisable(false);

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


    private void registerListenerForZoomSlider() {

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

    private void registerListenerForWindowingSliders() {

        minSlider.setDisable(false);
        maxSlider.setDisable(false);

        minSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable,
                                Number oldValue, Number newValue) {

                ImageProcessor ip = originalImage.getChannelProcessor();
                ip.setMinAndMax(newValue.doubleValue(), maxSlider.getValue());
                redrawCanvas();

            }
        });

        maxSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {

                ImageProcessor ip = originalImage.getChannelProcessor();
                ip.setMinAndMax(minSlider.getValue(), newValue.doubleValue());
                redrawCanvas();

            }
        });

    }

    private void resetWindowingSliders() {

        minSlider.adjustValue(0);
        maxSlider.adjustValue(255);

    }

    private void redrawCanvas() {

        Image currentImage = SwingFXUtils.
                toFXImage(originalImage.getBufferedImage(), null);

        canvas.setWidth(currentImage.getWidth());
        canvas.setHeight(currentImage.getHeight());

        canvas.getGraphicsContext2D().
                clearRect(0, 0, currentImage.getWidth(), currentImage.getHeight());

        canvas.getGraphicsContext2D().drawImage(currentImage, 0, 0);

    }


    public void clearSeeds(ActionEvent actionEvent) {

        getSeedImage(internalSeed).show();
        clearPoints();

    }

    private void extractFeatures() {

        GLCMtexture internalSeedGLCM = new GLCMtexture();
        internalSeedGLCM.calcGLCM(
                getSeedImage(internalSeed).getChannelProcessor());

        GLCMtexture externalSeedGLCM = new GLCMtexture();
        externalSeedGLCM.calcGLCM(
                getSeedImage(internalSeed).getChannelProcessor());


        System.out.println("Semente interna");
        System.out.println("Entropia : " + internalSeedGLCM.getEntropy());
        System.out.println("Homogeniedade : " + internalSeedGLCM.getHomogeneity());
        System.out.println("Energia: " + internalSeedGLCM.getEnergy());
        System.out.println("Contraste: " + internalSeedGLCM.getContrast());
        System.out.println("Correlação: " + internalSeedGLCM.getCorrelation());

        System.out.println("");

        System.out.println("Semente interna");
        System.out.println("Entropia : " + externalSeedGLCM.getEntropy());
        System.out.println("Homogeniedade : " + externalSeedGLCM.getHomogeneity());
        System.out.println("Energia: " + externalSeedGLCM.getEnergy());
        System.out.println("Contraste: " + externalSeedGLCM.getContrast());
        System.out.println("Correlação: " + externalSeedGLCM.getCorrelation());
    }

    public void runRegionGrowing(ActionEvent actionEvent) {

        // run this on a new thread
        // ImagePlus internal = getSeedImage(internal_seed);
        // ImagePlus external = getSeedImage(external_seed);
        // call_to_method(originalImage, internal, external);

    }

    private void onRegionGrowingComplete(ImagePlus imagePlus) {

        // originalImage = imagePlus;
        // redraw_canvas();

    }


    private ImagePlus getSeedImage(Seed seed) {


        if (seed.getShape() == Seed.Shape.Square) {

            ImageProcessor ip = originalImage.getChannelProcessor();

            Rectangle rec = (Rectangle) seed.getView().getChildren().get(0);

            int x = (int) rec.getX();
            int y = (int) rec.getY();
            int w = (int) rec.getWidth();
            int h = (int) rec.getHeight();

            ip.setRoi(x, y, w, h);
            ImageProcessor ip2 = ip.crop();

            return new ImagePlus("", ip2);
        }

        else if (seed.getShape() == Seed.Shape.Circle) {

            ImageProcessor ip = originalImage.getChannelProcessor();

            Circle circ = (Circle) seed.getView().getChildren().get(0);

            int x = (int) (circ.getCenterX() - circ.getRadius());
            int y = (int) (circ.getCenterY() - circ.getRadius());
            int w = (int) (2*circ.getRadius());

            Ellipse2D.Double shapeRoi = new Ellipse2D.Double(x, y, w, w);

            ip.setRoi(new ShapeRoi(shapeRoi));
            ImageProcessor ip2 = ip.crop();

            return new ImagePlus("", ip2);

        }

        return null;

    }


    public void selectSeedShape(ActionEvent actionEvent) {

        Button button = (Button) actionEvent.getSource();

        if (button.getId().equals("circle"))
            seedShape = Seed.Shape.Circle;

        else if (button.getId().equals("square"))
            seedShape = Seed.Shape.Square;

    }
}
