package com.prerequix.ui;

import com.prerequix.db.UserDatabaseManager;
import com.prerequix.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Modal settings dialog giving the logged-in student access to:
 * <ul>
 *   <li>Read-only account info (Student ID, username, registration date)</li>
 *   <li>Update their full name</li>
 *   <li>Change their password (requires current password verification)</li>
 * </ul>
 *
 * <p>The {@code onNameChanged} callback is invoked when the name is saved
 * successfully, allowing the sidebar in {@link MainController} to refresh.
 */
public class ProfileSettingsDialog extends Stage {

    private final User user;
    private final Consumer<String> onNameChanged;  // receives new full name

    public ProfileSettingsDialog(Window owner, User user, Consumer<String> onNameChanged) {
        this.user          = user;
        this.onNameChanged = onNameChanged;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Account Settings");
        setResizable(false);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                buildInfoTab(),
                buildUsernameTab(),
                buildNameTab(),
                buildPasswordTab()
        );

        VBox root = new VBox(tabs);
        root.setStyle("-fx-background-color: #f8fafc;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 460, 380);
        setScene(scene);
    }

    // ─── Tab 1: Account Info (read-only) ─────────────────────────────────

    private Tab buildInfoTab() {
        Tab tab = new Tab("Account Info");

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                infoRow("Full Name",          user.getFullName()),
                infoRow("Username",           user.getUsername()),
                infoRow("Student ID",         user.getStudentId()),
                infoRow("Account Created",    user.getCreatedAt()),
                infoRow("Account ID (UUID)",  user.getId())
        );

        tab.setContent(new ScrollPane(content));
        return tab;
    }

    private VBox infoRow(String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: 600; -fx-font-size: 11px; -fx-text-fill: #64748b;");

        Label val = new Label(value != null ? value : "—");
        val.setStyle("-fx-font-size: 13px; -fx-text-fill: #0f172a;");
        val.setWrapText(true);

        VBox box = new VBox(3, lbl, val);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 6; -fx-padding: 10 14;");
        return box;
    }

    // ─── Tab 2: Update Username ───────────────────────────────────────────

    private Tab buildUsernameTab() {
        Tab tab = new Tab("Update Username");

        Label heading = new Label("Change your login username");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        Label currentLbl = new Label("Current username: " + user.getUsername());
        currentLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        TextField usernameField = new TextField(user.getUsername());
        styleField(usernameField);
        usernameField.setPromptText("New username (min 3 chars, lowercase)");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);

        Label successLbl = new Label("Username updated successfully!");
        successLbl.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px;");
        successLbl.setVisible(false);

        Button saveBtn = new Button("Save Username");
        styleBtn(saveBtn);
        saveBtn.setOnAction(e -> {
            errorLbl.setVisible(false);
            successLbl.setVisible(false);
            String newUsername = usernameField.getText().trim().toLowerCase();
            if (newUsername.equals(user.getUsername())) {
                errorLbl.setText("That is already your current username.");
                errorLbl.setVisible(true); return;
            }
            try {
                UserDatabaseManager.getInstance().repository()
                        .updateUsername(user.getId(), newUsername);
                user.setUsername(newUsername);
                currentLbl.setText("Current username: " + newUsername);
                successLbl.setVisible(true);
            } catch (Exception ex) {
                errorLbl.setText(ex.getMessage());
                errorLbl.setVisible(true);
            }
        });

        VBox content = new VBox(14, heading, currentLbl,
                labeledField("New Username", usernameField),
                errorLbl, successLbl, saveBtn);
        content.setPadding(new Insets(24));
        tab.setContent(content);
        return tab;
    }

    // ─── Tab 3: Change Full Name ──────────────────────────────────────────

    private Tab buildNameTab() {
        Tab tab = new Tab("Update Name");

        Label heading = new Label("Update your display name");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        TextField nameField = new TextField(user.getFullName());
        styleField(nameField);

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
        errorLbl.setVisible(false);

        Label successLbl = new Label("Name updated successfully!");
        successLbl.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px;");
        successLbl.setVisible(false);

        Button saveBtn = new Button("Save Name");
        styleBtn(saveBtn);
        saveBtn.setOnAction(e -> {
            errorLbl.setVisible(false);
            successLbl.setVisible(false);
            String newName = nameField.getText().trim();
            if (newName.isBlank()) {
                errorLbl.setText("Name cannot be empty.");
                errorLbl.setVisible(true);
                return;
            }
            try {
                UserDatabaseManager.getInstance().repository()
                        .updateFullName(user.getId(), newName);
                user.setFullName(newName);
                successLbl.setVisible(true);
                if (onNameChanged != null) onNameChanged.accept(newName);
            } catch (Exception ex) {
                errorLbl.setText(ex.getMessage());
                errorLbl.setVisible(true);
            }
        });

        VBox content = new VBox(14, heading,
                labeledField("New Full Name", nameField),
                errorLbl, successLbl, saveBtn);
        content.setPadding(new Insets(24));
        tab.setContent(content);
        return tab;
    }

    // ─── Tab 3: Change Password ───────────────────────────────────────────

    private Tab buildPasswordTab() {
        Tab tab = new Tab("Change Password");

        Label heading = new Label("Change your password");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        PasswordField currentPwd  = new PasswordField();
        PasswordField newPwd      = new PasswordField();
        PasswordField confirmPwd  = new PasswordField();
        currentPwd.setPromptText("Current password");
        newPwd.setPromptText("New password (min 6 chars)");
        confirmPwd.setPromptText("Confirm new password");
        styleField(currentPwd); styleField(newPwd); styleField(confirmPwd);

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);

        Label successLbl = new Label("Password changed successfully!");
        successLbl.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px;");
        successLbl.setVisible(false);

        Button changeBtn = new Button("Change Password");
        styleBtn(changeBtn);
        changeBtn.setOnAction(e -> {
            errorLbl.setVisible(false);
            successLbl.setVisible(false);
            String cur  = currentPwd.getText();
            String np   = newPwd.getText();
            String conf = confirmPwd.getText();

            if (cur.isBlank() || np.isBlank() || conf.isBlank()) {
                errorLbl.setText("All fields are required.");
                errorLbl.setVisible(true); return;
            }
            // Verify current password
            String curHash = com.prerequix.db.SQLiteUserRepository.hashPassword(cur);
            if (!curHash.equals(user.getPasswordHash())) {
                errorLbl.setText("Current password is incorrect.");
                errorLbl.setVisible(true); return;
            }
            if (!np.equals(conf)) {
                errorLbl.setText("New passwords do not match.");
                errorLbl.setVisible(true); return;
            }
            if (np.length() < 6) {
                errorLbl.setText("Password must be at least 6 characters.");
                errorLbl.setVisible(true); return;
            }
            try {
                UserDatabaseManager.getInstance().repository()
                        .updatePassword(user.getId(), np);
                user.setPasswordHash(com.prerequix.db.SQLiteUserRepository.hashPassword(np));
                successLbl.setVisible(true);
                currentPwd.clear(); newPwd.clear(); confirmPwd.clear();
            } catch (Exception ex) {
                errorLbl.setText(ex.getMessage());
                errorLbl.setVisible(true);
            }
        });

        VBox content = new VBox(12, heading,
                labeledField("Current Password", currentPwd),
                labeledField("New Password",     newPwd),
                labeledField("Confirm Password", confirmPwd),
                errorLbl, successLbl, changeBtn);
        content.setPadding(new Insets(24));
        tab.setContent(new ScrollPane(content));
        return tab;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private VBox labeledField(String label, Region field) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: 600; -fx-font-size: 12px; -fx-text-fill: #374151;");
        return new VBox(4, lbl, field);
    }

    private void styleField(Region f) {
        f.setMaxWidth(Double.MAX_VALUE);
        f.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; " +
                   "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12; -fx-font-size: 13px;");
    }

    private void styleBtn(Button btn) {
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; " +
                     "-fx-font-weight: bold; -fx-font-size: 13px; " +
                     "-fx-padding: 10 0; -fx-background-radius: 7; -fx-cursor: hand;");
    }
}
