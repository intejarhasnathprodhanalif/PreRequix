package com.prerequix.ui;

import com.prerequix.db.UserDatabaseManager;
import com.prerequix.model.User;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Authentication screen shown before the main application.
 *
 * <p>Contains both Sign-In and Sign-Up panels on a split layout:
 * <ul>
 *   <li>Left – branded hero panel with gradient background.</li>
 *   <li>Right – card containing the active form (sign-in OR sign-up).</li>
 * </ul>
 *
 * <p>On successful login or registration the {@code onAuthSuccess} callback
 * is invoked with the authenticated {@link User}, allowing {@code App} to
 * swap the scene to {@link MainController}.
 *
 * <p><b>JavaFX controls used:</b> {@code PasswordField}, {@code TextField},
 * {@code Label}, {@code Button}, {@code VBox}, {@code HBox}, {@code StackPane},
 * {@code Pane}, {@code ScrollPane}, {@code Hyperlink}, {@code Separator}.
 */
public class AuthScreen extends HBox {

    private final Stage stage;
    private final Consumer<User> onAuthSuccess;
    private final UserDatabaseManager userDb;

    // ── Form state ────────────────────────────────────────────────────────
    private boolean isSignIn = true;

    // ── Sign-in widgets ───────────────────────────────────────────────────
    private final TextField     siUsername = new TextField();
    private final PasswordField siPassword = new PasswordField();
    private final Label         siError    = new Label();
    private final Button        siBtn      = new Button("Sign In");

    // ── Sign-up widgets ───────────────────────────────────────────────────
    private final TextField     suFullName = new TextField();
    private final TextField     suUsername = new TextField();
    private final PasswordField suPassword = new PasswordField();
    private final PasswordField suConfirm  = new PasswordField();
    private final Label         suError    = new Label();
    private final Button        suBtn      = new Button("Create Account");

    // ── Right panel (swappable) ───────────────────────────────────────────
    private final StackPane rightPanel = new StackPane();
    private final VBox signInCard;
    private final VBox signUpCard;

    public AuthScreen(Stage stage, Consumer<User> onAuthSuccess) {
        this.stage         = stage;
        this.onAuthSuccess = onAuthSuccess;
        this.userDb        = UserDatabaseManager.getInstance();

        setMinSize(900, 600);
        setPrefSize(940, 640);

        signInCard = buildSignInCard();
        signUpCard = buildSignUpCard();
        signUpCard.setVisible(false);
        signUpCard.setManaged(false);

        rightPanel.getChildren().addAll(signInCard, signUpCard);
        rightPanel.setStyle("-fx-background-color: #f8fafc;");

        getChildren().addAll(buildHeroPanel(), rightPanel);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
    }

    // ─── Left hero panel ──────────────────────────────────────────────────

    private Pane buildHeroPanel() {
        StackPane hero = new StackPane();
        hero.setPrefWidth(380);
        hero.setMinWidth(300);
        hero.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #1e3a8a, #3b82f6, #0ea5e9);"
        );

        // Decorative circles
        hero.getChildren().addAll(
            decorCircle(200, 200, -60, -60, 0.08),
            decorCircle(140, 140, 280, 450, 0.07),
            decorCircle(80,  80,  300, 60,  0.06)
        );

        // Branding
        Label appName = new Label("PreRequix");
        appName.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 36));
        appName.setTextFill(Color.WHITE);

        Label tagLine = new Label("Course Prerequisite Planner");
        tagLine.setFont(Font.font("Segoe UI", 14));
        tagLine.setTextFill(Color.web("#bfdbfe"));

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #60a5fa; -fx-opacity: 0.4;");
        sep.setMaxWidth(200);

        Label feat1 = heroFeature("Graph-based prerequisite visualisation");
        Label feat2 = heroFeature("Auto-generate your semester sequence");
        Label feat3 = heroFeature("Import curricula from the web");
        Label feat4 = heroFeature("Dark mode  |  Multi-threaded engine");

        Label copy = new Label("PreRequix v1.0  -  Your academic journey, planned.");
        copy.setFont(Font.font("Segoe UI", 11));
        copy.setTextFill(Color.web("#93c5fd"));
        copy.setWrapText(true);

        VBox content = new VBox(14, appName, tagLine, sep, feat1, feat2, feat3, feat4, copy);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(0, 30, 0, 40));
        content.setMaxWidth(340);

        hero.getChildren().add(content);
        StackPane.setAlignment(content, Pos.CENTER_LEFT);
        return hero;
    }

    private Label heroFeature(String text) {
        Label lbl = new Label("  " + text);
        lbl.setFont(Font.font("Segoe UI", 13));
        lbl.setTextFill(Color.web("#dbeafe"));
        lbl.setWrapText(true);
        lbl.setStyle("-fx-padding: 4 0 0 0;");
        return lbl;
    }

    private Circle decorCircle(double rx, double ry, double tx, double ty, double opacity) {
        Circle c = new Circle(rx / 2);
        c.setFill(Color.web("#ffffff", opacity));
        c.setTranslateX(tx);
        c.setTranslateY(ty);
        return c;
    }

    // ─── Sign-In card ─────────────────────────────────────────────────────

    private VBox buildSignInCard() {
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(0, 60, 0, 60));
        card.setMaxWidth(440);

        Label heading = new Label("Welcome back!");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        heading.setTextFill(Color.web("#0f172a"));

        Label sub = new Label("Sign in to access your course plan.");
        sub.setFont(Font.font("Segoe UI", 13));
        sub.setTextFill(Color.web("#64748b"));

        // Fields
        siUsername.setPromptText("Username");
        siPassword.setPromptText("Password");
        styleField(siUsername);
        styleField(siPassword);

        // Error
        siError.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
        siError.setWrapText(true);
        siError.setVisible(false);

        // Sign-in button
        styleBtn(siBtn, true);
        siBtn.setOnAction(e -> handleSignIn());

        // Enter key
        siPassword.setOnAction(e -> handleSignIn());
        siUsername.setOnAction(e -> siPassword.requestFocus());

        // Switch to sign-up
        Label switchLabel = new Label("Don't have an account?");
        switchLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        Hyperlink switchLink = new Hyperlink("Create one here");
        switchLink.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12px; -fx-border-color: transparent;");
        switchLink.setOnAction(e -> switchToSignUp());
        HBox switchRow = new HBox(4, switchLabel, switchLink);
        switchRow.setAlignment(Pos.CENTER);

        card.getChildren().addAll(
            heading, sub, new Separator(),
            labeledField("Username", siUsername),
            labeledField("Password", siPassword),
            siError, siBtn, switchRow
        );

        StackPane.setAlignment(card, Pos.CENTER);
        return card;
    }

    // ─── Sign-Up card ─────────────────────────────────────────────────────

    private VBox buildSignUpCard() {
        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(0, 60, 0, 60));
        card.setMaxWidth(440);

        Label heading = new Label("Create your account");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        heading.setTextFill(Color.web("#0f172a"));

        Label sub = new Label("A unique Student ID (PRQ-XXXXX) will be assigned to you.");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#64748b"));
        sub.setWrapText(true);

        suFullName.setPromptText("e.g. John Smith");
        suUsername.setPromptText("e.g. jsmith");
        suPassword.setPromptText("Minimum 6 characters");
        suConfirm.setPromptText("Re-enter password");

        for (var f : new Control[]{suFullName, suUsername, suPassword, suConfirm}) {
            styleField((Region) f);
        }

        suError.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
        suError.setWrapText(true);
        suError.setVisible(false);

        styleBtn(suBtn, true);
        suBtn.setOnAction(e -> handleSignUp());
        suConfirm.setOnAction(e -> handleSignUp());

        Hyperlink switchLink = new Hyperlink("Already have an account? Sign in");
        switchLink.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12px; -fx-border-color: transparent;");
        switchLink.setOnAction(e -> switchToSignIn());

        card.getChildren().addAll(
            heading, sub, new Separator(),
            labeledField("Full Name",         suFullName),
            labeledField("Username",          suUsername),
            labeledField("Password",          suPassword),
            labeledField("Confirm Password",  suConfirm),
            suError, suBtn, switchLink
        );

        StackPane.setAlignment(card, Pos.CENTER);
        return card;
    }

    // ─── Handlers ─────────────────────────────────────────────────────────

    private void handleSignIn() {
        siError.setVisible(false);
        String username = siUsername.getText().trim();
        String password = siPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(siError, "Please enter your username and password.");
            return;
        }

        try {
            User user = userDb.repository().login(username, password);
            onAuthSuccess.accept(user);
        } catch (Exception ex) {
            showError(siError, ex.getMessage());
            shake(signInCard);
        }
    }

    private void handleSignUp() {
        suError.setVisible(false);
        String fullName = suFullName.getText().trim();
        String username = suUsername.getText().trim();
        String password = suPassword.getText();
        String confirm  = suConfirm.getText();

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError(suError, "All fields are required.");
            return;
        }
        if (!password.equals(confirm)) {
            showError(suError, "Passwords do not match.");
            shake(signUpCard);
            return;
        }
        if (password.length() < 6) {
            showError(suError, "Password must be at least 6 characters.");
            return;
        }

        try {
            User user = userDb.repository().register(fullName, username, password);

            // Show the assigned student ID before entering the app
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.initOwner(stage);
            ok.setTitle("Account Created!");
            ok.setHeaderText("Welcome, " + user.getFullName() + "!");
            ok.setContentText(
                "Your account has been created successfully.\n\n" +
                "Your Student ID:  " + user.getStudentId() + "\n" +
                "Username:         " + user.getUsername() + "\n\n" +
                "Please save your Student ID for your records."
            );
            ok.showAndWait();
            onAuthSuccess.accept(user);

        } catch (Exception ex) {
            showError(suError, ex.getMessage());
            shake(signUpCard);
        }
    }

    // ─── Transitions ──────────────────────────────────────────────────────

    private void switchToSignUp() {
        fade(signInCard, 1, 0, () -> {
            signInCard.setVisible(false);
            signInCard.setManaged(false);
            clearForm();
            signUpCard.setVisible(true);
            signUpCard.setManaged(true);
            fade(signUpCard, 0, 1, null);
        });
        isSignIn = false;
    }

    private void switchToSignIn() {
        fade(signUpCard, 1, 0, () -> {
            signUpCard.setVisible(false);
            signUpCard.setManaged(false);
            clearForm();
            signInCard.setVisible(true);
            signInCard.setManaged(true);
            fade(signInCard, 0, 1, null);
        });
        isSignIn = true;
    }

    private void fade(javafx.scene.Node node, double from, double to, Runnable onDone) {
        FadeTransition ft = new FadeTransition(Duration.millis(220), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        if (onDone != null) ft.setOnFinished(e -> onDone.run());
        ft.play();
    }

    private void shake(javafx.scene.Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), node);
        tt.setByX(8);
        tt.setCycleCount(4);
        tt.setAutoReverse(true);
        tt.play();
    }

    private void showError(Label label, String msg) {
        label.setText(msg);
        label.setVisible(true);
        fade(label, 0, 1, null);
    }

    private void clearForm() {
        for (TextField f : new TextField[]{
                siUsername, siPassword, suFullName, suUsername, suPassword, suConfirm}) {
            f.clear();
        }
        siError.setVisible(false);
        suError.setVisible(false);
    }

    // ─── UI helpers ───────────────────────────────────────────────────────

    private VBox labeledField(String labelText, Region field) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-weight: 600; -fx-font-size: 12px; -fx-text-fill: #374151;");
        VBox box = new VBox(5, lbl, field);
        return box;
    }

    private void styleField(Region field) {
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #d1d5db;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 7px;" +
            "-fx-background-radius: 7px;" +
            "-fx-padding: 9px 13px;" +
            "-fx-font-size: 13px;" +
            "-fx-text-fill: #0f172a;"
        );
        // Focus effect via listener
        field.focusedProperty().addListener((obs, old, focused) -> {
            if (focused) {
                field.setStyle(field.getStyle().replace(
                    "-fx-border-color: #d1d5db;",
                    "-fx-border-color: #3b82f6;"));
            } else {
                field.setStyle(field.getStyle().replace(
                    "-fx-border-color: #3b82f6;",
                    "-fx-border-color: #d1d5db;"));
            }
        });
    }

    private void styleBtn(Button btn, boolean primary) {
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
            "-fx-background-color: #3b82f6;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 14px;" +
            "-fx-padding: 11px 0;" +
            "-fx-background-radius: 7px;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle()
            .replace("#3b82f6", "#2563eb")));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle()
            .replace("#2563eb", "#3b82f6")));
    }
}