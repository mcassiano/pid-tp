package me.cassiano.tp_pid;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class ImageProcessingController implements Initializable {

    @FXML
    private ImageView histogram;

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

    private ImageView currentImage;

    private DoubleProperty sliderZoomProperty = new SimpleDoubleProperty(100);

    private Image histo;

    private boolean pickingSeed = false;

    private Circle internalSeed;
    private Circle externalSeed;

    private Seed seedBeingPicked;


    private enum Seed {
        Internal, External;
    }



    @Override
    public void initialize(URL location, ResourceBundle resources) {

        currentImage = new ImageView();

        currentImage.setId("imageView");

        zoomGroup = new Group();
        zoomGroup.getChildren().add(currentImage);

        rootGroup.getChildren().add(zoomGroup);

    }


    public void openImageClicked(ActionEvent actionEvent) {

        if (currentImage.getImage() == null) {
            registerScrollListener();
            registerSliderListener();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar imagem");
        File file = fileChooser.showOpenDialog(currentImage.getScene().getWindow());


        if (file != null) {

            Mat currentMat = Highgui.imread(file.getAbsolutePath());
            Imgproc.cvtColor(currentMat, currentMat, Imgproc.COLOR_BGR2GRAY);

            Image image = mat2Image(currentMat);

            currentImage.setImage(image);

            showHistogram(currentMat, true);

            enableSeedButtons();
        }

    }

    private void clearPoints() {

        List<Node> nodesToBeRemoved = new ArrayList<Node>();

        for (Node node : zoomGroup.getChildren()) {
            if (node.getId() == null)
                nodesToBeRemoved.add(node);
        }

        zoomGroup.getChildren().removeAll(nodesToBeRemoved);

        internalSeed = null;
        externalSeed = null;


    }

    private void registerImageViewOnClickListener() {

        currentImage.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                System.out.println("X: " + event.getX());
                System.out.println("Y: " + event.getY());


                Circle circle = new Circle();
                circle.setRadius(5);
                circle.setCenterX(event.getX());
                circle.setCenterY(event.getY());

                if (seedBeingPicked == Seed.Internal) {

                    circle.setFill(Paint.valueOf("GREEN"));

                    if (internalSeed != null)
                        zoomGroup.getChildren().remove(internalSeed);

                    internalSeed = circle;
                }

                else if (seedBeingPicked == Seed.External) {

                    circle.setFill(Paint.valueOf("BLUE"));

                    if (externalSeed != null)
                        zoomGroup.getChildren().remove(externalSeed);

                    externalSeed = circle;
                }

                zoomGroup.getChildren().add(circle);

                currentImage.setOnMouseClicked(null);
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

    private void zoomToActualSize() {

        sliderZoomProperty.set(100);
        zoomSlider.setValue(100);

    }

    public void inSeedClicked(ActionEvent actionEvent) {

        //zoomToActualSize();
        seedBeingPicked = Seed.Internal;
        disableSeedButtons();
        registerImageViewOnClickListener();

    }

    public void outSeedClicked(ActionEvent actionEvent) {

        //zoomToActualSize();
        seedBeingPicked = Seed.External;
        disableSeedButtons();
        registerImageViewOnClickListener();
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

    private void showHistogram(Mat frame, boolean gray)
    {
        // split the frames in multiple images
        List<Mat> images = new ArrayList<Mat>();
        Core.split(frame, images);

        // set the number of bins at 256
        MatOfInt histSize = new MatOfInt(256);
        // only one channel
        MatOfInt channels = new MatOfInt(0);
        // set the ranges
        MatOfFloat histRange = new MatOfFloat(0, 256);

        // compute the histograms for the B, G and R components
        Mat hist_b = new Mat();
        Mat hist_g = new Mat();
        Mat hist_r = new Mat();

        // B component or gray image
        Imgproc.calcHist(images.subList(0, 1), channels, new Mat(), hist_b, histSize, histRange, false);

        // G and R components (if the image is not in gray scale)
        if (!gray)
        {
            Imgproc.calcHist(images.subList(1, 2), channels, new Mat(), hist_g, histSize, histRange, false);
            Imgproc.calcHist(images.subList(2, 3), channels, new Mat(), hist_r, histSize, histRange, false);
        }

        // draw the histogram
        int hist_w = 215; // width of the histogram image
        int hist_h = 215; // height of the histogram image
        int bin_w = (int) Math.round(hist_w / histSize.get(0, 0)[0]);

        Mat histImage = new Mat(hist_h, hist_w, CvType.CV_8UC3, new Scalar(0, 0, 0));
        // normalize the result to [0, histImage.rows()]
        Core.normalize(hist_b, hist_b, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());

        // for G and R components
        if (!gray)
        {
            Core.normalize(hist_g, hist_g, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());
            Core.normalize(hist_r, hist_r, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());
        }

        // effectively draw the histogram(s)
        for (int i = 1; i < histSize.get(0, 0)[0]; i++)
        {
            // B component or gray image
            Core.line(histImage, new Point(bin_w * (i - 1), hist_h - Math.round(hist_b.get(i - 1, 0)[0])), new Point(
                    bin_w * (i), hist_h - Math.round(hist_b.get(i, 0)[0])), new Scalar(255, 0, 0), 2, 8, 0);
            // G and R components (if the image is not in gray scale)
            if (!gray)
            {
                Core.line(histImage, new Point(bin_w * (i - 1), hist_h - Math.round(hist_g.get(i - 1, 0)[0])),
                        new Point(bin_w * (i), hist_h - Math.round(hist_g.get(i, 0)[0])), new Scalar(0, 255, 0), 2, 8,
                        0);
                Core.line(histImage, new Point(bin_w * (i - 1), hist_h - Math.round(hist_r.get(i - 1, 0)[0])),
                        new Point(bin_w * (i), hist_h - Math.round(hist_r.get(i, 0)[0])), new Scalar(0, 0, 255), 2, 8,
                        0);
            }
        }

        histo = mat2Image(histImage);

        // display the whole
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                histogram.setImage(histo);
            }
        });

    }

    private Image mat2Image(Mat frame)
    {
        MatOfByte buffer = new MatOfByte();
        Highgui.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }

    public void clearSeeds(ActionEvent actionEvent) {

        clearPoints();

    }
}
