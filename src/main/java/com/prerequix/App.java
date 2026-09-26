package com.prerequix;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.db.DatabaseManager;
import com.prerequix.db.UserDatabaseManager;
import com.prerequix.model.User;
import com.prerequix.ui.AuthScreen;
import com.prerequix.ui.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main JavaFX Application Launcher for PreRequix.
 *
 * <p>Startup sequence:
 * <ol>
 *   <li>Initialize {@code users.db} (student accounts).</li>
 *   <li>Initialize {@code prerequix.db} (course data).</li>
 *   <li>Show the {@link AuthScreen} (sign-in / sign-up).</li>
 *   <li>On successful authentication, swap the scene to {@link MainController}.</li>
 * </ol>
 */
public class App extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("PreRequix - Course Prerequisite Planner");

        // ── Initialize databases ──────────────────────────────────────────
        try {
            UserDatabaseManager.getInstance().initialize();
        } catch (Exception e) {
            System.err.println("[UserDB] Init failed: " + e.getMessage());
        }
        try {
            DatabaseManager.getInstance().initialize();
        } catch (Exception e) {
            System.err.println("[DB] Init failed: " + e.getMessage());
        }

        // ── Show authentication screen ────────────────────────────────────
        showAuthScreen();

        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    /** Builds and displays the sign-in / sign-up scene. */
    private void showAuthScreen() {
        AuthScreen authScreen = new AuthScreen(primaryStage, this::onLoginSuccess);
        Scene scene = new Scene(authScreen, 940, 640);
        applyStylesheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);   // fixed size for auth
    }

    /**
     * Called by {@link AuthScreen} when the user authenticates successfully.
     * Replaces the auth scene with the full main application.
     */
    private void onLoginSuccess(User user) {
        System.out.println("[Auth] Logged in: " + user);

        MainController mainController = new MainController(primaryStage);
        Scene scene = new Scene(mainController, 1280, 800);
        applyStylesheet(scene);

        primaryStage.setResizable(true);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(650);
        primaryStage.setScene(scene);
        primaryStage.setTitle("PreRequix  -  " + user.getFullName() +
                              " (" + user.getStudentId() + ")");
    }

    private void applyStylesheet(Scene scene) {
        URL cssUrl = getClass().getResource("/css/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
    }

    @Override
    public void stop() {
        AppExecutor.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}