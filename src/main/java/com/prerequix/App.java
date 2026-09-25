package com.prerequix;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.ui.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main JavaFX Application Launcher for PreRequix - Course Prerequisite Planner.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("PreRequix - Course Prerequisite Planner");

        MainController root = new MainController(primaryStage);
        Scene scene = new Scene(root, 1280, 800);

        URL cssUrl = getClass().getResource("/css/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Warning: /css/style.css not found in resources!");
        }

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(650);
        primaryStage.show();
    }

    /**
     * Called by the JavaFX runtime when the application window closes.
     * Shuts down both thread-pool executors in AppExecutor so the JVM
     * can exit cleanly without waiting for daemon threads.
     */
    @Override
    public void stop() {
        AppExecutor.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
