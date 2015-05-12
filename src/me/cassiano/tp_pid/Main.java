package me.cassiano.tp_pid;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.opencv.core.Core;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("ImageProcessing.fxml"));

        primaryStage.setTitle("Trabalho Prático - PID");
        primaryStage.setScene(new Scene(root, 800, 660));
        primaryStage.setResizable(false);
        primaryStage.show();
    }


    public static void main(String[] args) {

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        launch(args);
    }
}
