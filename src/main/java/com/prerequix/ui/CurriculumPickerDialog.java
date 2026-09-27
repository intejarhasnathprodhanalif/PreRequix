package com.prerequix.ui;

import com.prerequix.db.DatabaseManager;
import com.prerequix.db.StudentCurriculumRepository.CurriculumInfo;
import com.prerequix.model.Course;
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

import java.util.List;

/**
 * Modal dialog that lets a student choose one of the predefined curriculum
 * templates. Shown when the student has no saved curriculum yet.
 *
 * <p>Displays curriculum cards in a 3-column grid. Clicking a card highlights
 * it and loads a course preview list. "Choose This Curriculum" confirms the
 * selection and seeds the student's personal course list from the template.
 */
public class CurriculumPickerDialog extends Stage {

    private String selectedCurriculumId = null;
    private final String studentId;
    private final Label previewTitle   = new Label();
    private final VBox  previewList    = new VBox(6);
    private final Button confirmBtn;
    private List<CurriculumInfo> curricula;

    public CurriculumPickerDialog(Window owner, String studentId) {
        this.studentId = studentId;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Choose Your Curriculum");
        setResizable(false);

        confirmBtn = new Button("Choose This Curriculum");
        confirmBtn.setDisable(true);
        confirmBtn.setStyle(
            "-fx-background-color: #3b82f6; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-padding: 10 24; -fx-background-radius: 7; -fx-cursor: hand;");
        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(confirmBtn.getStyle().replace("#3b82f6","#2563eb")));
        confirmBtn.setOnMouseExited(e  -> confirmBtn.setStyle(confirmBtn.getStyle().replace("#2563eb","#3b82f6")));
        confirmBtn.setOnAction(e -> onConfirm());

        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(buildBody());
        root.setBottom(buildFooter());
        root.setStyle("-fx-background-color: #f8fafc;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 960, 680);
        setScene(scene);
    }

    // ─── Header ───────────────────────────────────────────────────────────

    private VBox buildHeader() {
        Label h = new Label("Select Your Curriculum");
        h.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        h.setTextFill(Color.web("#0f172a"));

        Label sub = new Label("Choose the programme that best matches your academic journey. You can add or remove courses after selecting.");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        sub.setWrapText(true);

        VBox box = new VBox(6, h, sub);
        box.setPadding(new Insets(24, 28, 14, 28));
        box.setStyle("-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 1;");
        return box;
    }

    // ─── Body ─────────────────────────────────────────────────────────────

    private HBox buildBody() {
        // Left: curriculum cards grid
        FlowPane grid = new FlowPane(14, 14);
        grid.setPadding(new Insets(18, 14, 18, 14));
        grid.setPrefWrapLength(560);

        try {
            curricula = DatabaseManager.getInstance().studentRepository().getAllCurricula();
            for (CurriculumInfo ci : curricula) {
                grid.getChildren().add(buildCard(ci));
            }
        } catch (Exception ex) {
            grid.getChildren().add(new Label("Error loading curricula: " + ex.getMessage()));
        }

        ScrollPane scrollGrid = new ScrollPane(grid);
        scrollGrid.setFitToWidth(true);
        scrollGrid.setFitToHeight(true);
        scrollGrid.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // Right: course preview
        VBox rightPanel = buildPreviewPanel();

        HBox body = new HBox(0, scrollGrid, rightPanel);
        HBox.setHgrow(scrollGrid, Priority.ALWAYS);
        return body;
    }

    private VBox buildCard(CurriculumInfo ci) {
        Label name   = new Label(ci.name());
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        name.setTextFill(Color.web("#1e293b"));
        name.setWrapText(true);

        Label degree = new Label(ci.degree());
        degree.setStyle("-fx-font-size: 11px; -fx-text-fill: #3b82f6; -fx-font-weight: 600;");

        Label dept   = new Label(ci.department());
        dept.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Label count  = new Label(ci.totalCourses() + " courses");
        count.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        HBox meta    = new HBox(8, dept, new Label("·"), count);
        meta.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, name, degree, meta);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setPrefWidth(240);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 10;" +
            "-fx-background-radius: 10;" +
            "-fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 4, 0, 0, 2);"
        );

        card.setOnMouseClicked(e -> selectCard(card, ci));
        card.setOnMouseEntered(e -> {
            if (!ci.id().equals(selectedCurriculumId))
                card.setStyle(card.getStyle().replace("#e2e8f0", "#93c5fd"));
        });
        card.setOnMouseExited(e -> {
            if (!ci.id().equals(selectedCurriculumId))
                card.setStyle(card.getStyle().replace("#93c5fd", "#e2e8f0"));
        });

        return card;
    }

    private VBox buildPreviewPanel() {
        previewTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        previewTitle.setTextFill(Color.web("#1e293b"));
        previewTitle.setText("← Select a curriculum to preview courses");
        previewTitle.setWrapText(true);

        previewList.setPadding(new Insets(0, 0, 0, 0));
        ScrollPane scroll = new ScrollPane(previewList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox panel = new VBox(12, previewTitle, scroll);
        panel.setPadding(new Insets(18, 20, 18, 16));
        panel.setPrefWidth(340);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        panel.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: transparent transparent transparent #e2e8f0; -fx-border-width: 1;");
        return panel;
    }

    // ─── Card selection ───────────────────────────────────────────────────

    private VBox selectedCard = null;

    private void selectCard(VBox card, CurriculumInfo ci) {
        // Deselect old
        if (selectedCard != null) {
            selectedCard.setStyle(selectedCard.getStyle()
                .replace("-fx-border-color: #3b82f6;", "-fx-border-color: #e2e8f0;")
                .replace("-fx-background-color: #eff6ff;", "-fx-background-color: white;"));
        }
        selectedCard = card;
        selectedCurriculumId = ci.id();

        card.setStyle(card.getStyle()
            .replace("-fx-border-color: #e2e8f0;", "-fx-border-color: #3b82f6;")
            .replace("-fx-background-color: white;", "-fx-background-color: #eff6ff;"));

        confirmBtn.setDisable(false);
        confirmBtn.setText("Choose: " + ci.name());

        // Load course preview
        loadPreview(ci);
    }

    private void loadPreview(CurriculumInfo ci) {
        previewTitle.setText(ci.name() + "  (" + ci.totalCourses() + " courses)");
        previewList.getChildren().clear();

        Label loading = new Label("Loading courses...");
        loading.setStyle("-fx-text-fill: #94a3b8;");
        previewList.getChildren().add(loading);

        new Thread(() -> {
            try {
                List<Course> courses = DatabaseManager.getInstance()
                        .studentRepository().getCurriculumCourses(ci.id());
                javafx.application.Platform.runLater(() -> {
                    previewList.getChildren().clear();
                    for (Course c : courses) {
                        HBox row = new HBox(10);
                        Label code = new Label(c.getCode());
                        code.setStyle("-fx-font-weight: 600; -fx-font-size: 11px; -fx-text-fill: #3b82f6; -fx-min-width: 70;");
                        Label title = new Label(c.getTitle());
                        title.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
                        title.setWrapText(true);
                        Label cr = new Label(c.getCredits() + " cr");
                        cr.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                        row.getChildren().addAll(code, title, new Region(), cr);
                        HBox.setHgrow(title, Priority.ALWAYS);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(5, 8, 5, 8));
                        row.setStyle("-fx-background-color: white; -fx-background-radius: 5;");
                        previewList.getChildren().add(row);
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                    previewList.getChildren().add(new Label("Error: " + ex.getMessage())));
            }
        }, "PreviewLoader").start();
    }

    // ─── Footer ───────────────────────────────────────────────────────────

    private HBox buildFooter() {
        Label note = new Label("You can add, remove, or edit courses after choosing a curriculum.");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #374151; -fx-font-size: 13px; -fx-padding: 9 20; -fx-background-radius: 7; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> close());

        HBox row = new HBox(12, note, new Region(), cancelBtn, confirmBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        row.setPadding(new Insets(14, 24, 18, 24));
        row.setStyle("-fx-border-color: #e2e8f0 transparent transparent transparent; -fx-border-width: 1;");
        return row;
    }

    // ─── Confirm ──────────────────────────────────────────────────────────

    private void onConfirm() {
        if (selectedCurriculumId == null) return;
        try {
            DatabaseManager.getInstance().studentRepository()
                    .chooseAndInitCurriculum(studentId, selectedCurriculumId);
            close();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to save curriculum: " + ex.getMessage()).showAndWait();
        }
    }

    /** Returns the curriculum ID the student confirmed, or null if cancelled. */
    public String getChosenCurriculumId() { return selectedCurriculumId; }
}