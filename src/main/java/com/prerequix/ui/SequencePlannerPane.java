package com.prerequix.ui;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.concurrent.GraphComputeTask;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.SemesterPlan;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Visual term-by-term roadmap sequence generator and semester planner.
 * Includes an "Edit Plan" mode that lets students manually move courses
 * between terms, with live prerequisite-completion warnings.
 */
public class SequencePlannerPane extends BorderPane {

    private final Stage primaryStage;
    private final CourseGraph graph;
    private final Consumer<Course> onCourseSelectedListener;

    private final Slider creditSlider;
    private final Label creditValLabel;
    private final Label summaryStatsLabel;
    private final VBox termsContainer;

    /** Latest computed plan – kept so the Edit dialog can re-read it. */
    private List<SemesterPlan> lastPlan = new ArrayList<>();
    private boolean lastHadCycle = false;

    public SequencePlannerPane(Stage primaryStage, CourseGraph graph,
                               Consumer<Course> onCourseSelectedListener) {
        this.primaryStage = primaryStage;
        this.graph = graph;
        this.onCourseSelectedListener = onCourseSelectedListener;

        setPadding(new Insets(16));

        // ── Top Control Header ────────────────────────────────────────────
        VBox topHeader = new VBox(10);
        topHeader.getStyleClass().add("card-panel");
        topHeader.setPadding(new Insets(14, 18, 14, 18));

        Label title = new Label("Valid Course-Taking Sequence Planner");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitle = new Label(
                "Calculates topological course order and distributes remaining courses into terms based on credit limits.");
        subtitle.getStyleClass().add("muted-text");

        Label sliderTitle = new Label("Max Credits Per Term:");
        sliderTitle.setStyle("-fx-font-weight: 600;");

        creditSlider = new Slider(6.0, 24.0, 15.0);
        creditSlider.setMajorTickUnit(3.0);
        creditSlider.setMinorTickCount(2);
        creditSlider.setSnapToTicks(true);
        creditSlider.setShowTickMarks(true);
        creditSlider.setShowTickLabels(true);
        creditSlider.setPrefWidth(220);

        creditValLabel = new Label("15.0 cr / term");
        creditValLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        creditSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            creditValLabel.setText(String.format("%.1f cr / term", newVal.doubleValue()));
            generateRoadmap();
        });

        HBox sliderBox = new HBox(10, sliderTitle, creditSlider, creditValLabel);
        sliderBox.setAlignment(Pos.CENTER_LEFT);

        summaryStatsLabel = new Label();
        summaryStatsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

        Button editPlanBtn = new Button("Edit Plan");
        editPlanBtn.getStyleClass().add("btn-secondary");
        editPlanBtn.setOnAction(e -> openEditPlanDialog());

        Button exportBtn = new Button("Export Schedule");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setOnAction(e -> exportScheduleReport());

        HBox controlsRow = new HBox(10, sliderBox, summaryStatsLabel, new Region(), editPlanBtn, exportBtn);
        HBox.setHgrow(controlsRow.getChildren().get(2), Priority.ALWAYS);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        topHeader.getChildren().addAll(title, subtitle, new Separator(), controlsRow);
        setTop(topHeader);

        // ── Center Scroll Pane ───────────────────────────────────────────
        termsContainer = new VBox(16);
        termsContainer.setPadding(new Insets(16, 0, 16, 0));

        ScrollPane scrollPane = new ScrollPane(termsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        setCenter(scrollPane);
        generateRoadmap();
    }

    // ─── Public API called by MainController ─────────────────────────────

    public double getCurrentMaxCredits() {
        return creditSlider.getValue();
    }

    /**
     * Submits a background GraphComputeTask to regenerate the plan.
     * Called from the slider listener and when switching to this view.
     */
    public void generateRoadmap() {
        double maxCredits = creditSlider.getValue();
        GraphComputeTask task = new GraphComputeTask(graph, maxCredits);
        task.setOnSucceeded(e -> Platform.runLater(() ->
                applyComputedPlan(task.getValue().semesterPlan, task.getValue().hasCycle())));
        task.setOnFailed(e -> Platform.runLater(() -> {
            termsContainer.getChildren().clear();
            Label err = new Label("Cannot generate schedule: " + task.getException().getMessage());
            err.getStyleClass().add("conflict-text");
            termsContainer.getChildren().add(err);
            summaryStatsLabel.setText("Error");
        }));
        AppExecutor.getInstance().computePool().submit(task);
    }

    /**
     * Applies a pre-computed semester plan to the UI (always on the FX thread).
     */
    public void applyComputedPlan(List<SemesterPlan> schedule, boolean hasCycle) {
        this.lastPlan = schedule == null ? new ArrayList<>() : new ArrayList<>(schedule);
        this.lastHadCycle = hasCycle;
        renderPlan(lastPlan, hasCycle);
    }

    // ─── Rendering ────────────────────────────────────────────────────────

    private void renderPlan(List<SemesterPlan> schedule, boolean hasCycle) {
        termsContainer.getChildren().clear();
        double maxCredits = creditSlider.getValue();

        if (hasCycle) {
            Label err = new Label("Cannot generate schedule: circular prerequisite dependency detected!");
            err.getStyleClass().add("conflict-text");
            termsContainer.getChildren().add(err);
            summaryStatsLabel.setText("Conflict Detected");
            return;
        }
        if (schedule.isEmpty()) {
            Label empty = new Label("All courses in your curriculum are completed!");
            empty.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
            termsContainer.getChildren().add(empty);
            summaryStatsLabel.setText("0 Remaining Terms Required");
            return;
        }

        double total = schedule.stream().mapToDouble(SemesterPlan::getTotalCredits).sum();
        summaryStatsLabel.setText(String.format("%d Term%s Required (%.1f Total Credits)",
                schedule.size(), schedule.size() > 1 ? "s" : "", total));

        FlowPane grid = new FlowPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (SemesterPlan plan : schedule) {
            grid.getChildren().add(createTermCard(plan, maxCredits));
        }
        termsContainer.getChildren().add(grid);
    }

    private VBox createTermCard(SemesterPlan plan, double maxCredits) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPrefWidth(300);

        Label termHeader = new Label("Semester / Term " + plan.getTermNumber());
        termHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        Label creditBadge = new Label(String.format("%.1f / %.1f Credits",
                plan.getTotalCredits(), maxCredits));
        creditBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #64748b;");

        HBox top = new HBox(10, termHeader, new Region(), creditBadge);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);

        ProgressBar progress = new ProgressBar(plan.getTotalCredits() / maxCredits);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setStyle("-fx-accent: #3b82f6;");

        VBox coursesList = new VBox(5);
        for (Course c : plan.getCourses()) {
            boolean prereqsMet = allPrereqsCompleted(c);
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 8, 5, 8));
            String bg = prereqsMet ? "#f1f5f9" : "#fff7ed";
            row.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 6px; -fx-cursor: hand;");

            Label codeLabel = new Label(c.getCode());
            codeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            codeLabel.setMinWidth(Region.USE_PREF_SIZE);

            Label titleLabel = new Label(c.getTitle());
            titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label creditLabel = new Label(c.getCredits() + " cr");
            creditLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600;");
            creditLabel.setMinWidth(Region.USE_PREF_SIZE);

            row.getChildren().addAll(codeLabel, titleLabel, creditLabel);

            if (!prereqsMet) {
                Label warn = new Label("Prereq incomplete!");
                warn.setStyle("-fx-font-size: 10px; -fx-text-fill: #f97316; -fx-font-weight: bold;");
                warn.setMinWidth(Region.USE_PREF_SIZE);
                row.getChildren().add(warn);
                Tooltip.install(row, new Tooltip(
                        "Warning: Not all prerequisites of " + c.getCode() +
                        " are marked Completed. Verify before enrolling."));
            }

            row.setOnMouseClicked(e -> {
                if (onCourseSelectedListener != null) onCourseSelectedListener.accept(c);
            });
            coursesList.getChildren().add(row);
        }

        card.getChildren().addAll(top, progress, new Separator(), coursesList);
        return card;
    }

    /** Returns true only if every direct prerequisite of this course is COMPLETED. */
    private boolean allPrereqsCompleted(Course c) {
        return graph.getDirectPrerequisites(c.getId()).stream()
                .allMatch(Course::isCompleted);
    }

    // ─── Edit Plan Dialog ─────────────────────────────────────────────────

    private void openEditPlanDialog() {
        if (lastHadCycle) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.initOwner(primaryStage);
            a.setTitle("Cannot Edit Plan");
            a.setHeaderText("Circular dependency detected");
            a.setContentText("Resolve circular prerequisites before editing the plan.");
            a.showAndWait();
            return;
        }
        if (lastPlan.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.initOwner(primaryStage);
            a.setTitle("Nothing to Edit");
            a.setHeaderText("All courses completed or no plan generated yet.");
            a.showAndWait();
            return;
        }

        // Build a mutable copy: termIndex -> list of course IDs
        List<List<String>> termCourseIds = lastPlan.stream()
                .map(p -> p.getCourses().stream().map(Course::getId).collect(Collectors.toList()))
                .collect(Collectors.toList());

        // ── Dialog ───────────────────────────────────────────────────────
        Stage dlg = new Stage();
        dlg.initOwner(primaryStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Semester Plan");

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));

        Label header = new Label("Edit Semester Plan");
        header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label hint = new Label(
                "Select a course and use Move Up / Move Down to shift it to an earlier or later term.\n" +
                "Courses with unmet prerequisites are highlighted in orange - you will be warned before saving.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        // Flat list view: each entry formatted as "Term N | CODE - Title"
        ListView<String> listView = new ListView<>();
        listView.setPrefHeight(320);
        refreshEditList(listView, termCourseIds);

        Label warningLabel = new Label();
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: #f97316; -fx-font-weight: bold; -fx-font-size: 12px;");

        Button moveUpBtn = new Button("Move to Earlier Term");
        moveUpBtn.getStyleClass().add("btn-secondary");
        Button moveDownBtn = new Button("Move to Later Term");
        moveDownBtn.getStyleClass().add("btn-secondary");

        moveUpBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            if (sel < 0) return;
            int[] pos = flatIndexToTermCourse(termCourseIds, sel);
            if (pos == null || pos[0] == 0) return;                 // already in term 1
            String id = termCourseIds.get(pos[0]).remove(pos[1]);
            termCourseIds.get(pos[0] - 1).add(id);
            refreshEditList(listView, termCourseIds);
            warningLabel.setText(checkPrereqWarnings(termCourseIds));
        });

        moveDownBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            if (sel < 0) return;
            int[] pos = flatIndexToTermCourse(termCourseIds, sel);
            if (pos == null || pos[0] == termCourseIds.size() - 1) return;
            // If last term has only 1 course, add a new term
            if (pos[0] == termCourseIds.size() - 1) {
                termCourseIds.add(new ArrayList<>());
            } else if (termCourseIds.get(pos[0] + 1) == null) {
                termCourseIds.add(new ArrayList<>());
            }
            String id = termCourseIds.get(pos[0]).remove(pos[1]);
            // Ensure target term exists
            while (termCourseIds.size() <= pos[0] + 1) termCourseIds.add(new ArrayList<>());
            termCourseIds.get(pos[0] + 1).add(id);
            // Remove empty terms
            termCourseIds.removeIf(List::isEmpty);
            refreshEditList(listView, termCourseIds);
            warningLabel.setText(checkPrereqWarnings(termCourseIds));
        });

        HBox moveBtns = new HBox(10, moveUpBtn, moveDownBtn);
        moveBtns.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Apply Plan");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            String warnings = checkPrereqWarnings(termCourseIds);
            if (!warnings.isEmpty()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.initOwner(dlg);
                confirm.setTitle("Prerequisites Not Fully Met");
                confirm.setHeaderText("Some courses have incomplete prerequisites.");
                confirm.setContentText(warnings + "\n\nDo you still want to apply this plan?");
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) return;
            }
            // Rebuild SemesterPlan list from edited term/course mapping
            List<SemesterPlan> edited = buildPlanFromIds(termCourseIds);
            applyComputedPlan(edited, false);
            dlg.close();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> dlg.close());

        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, hint, listView, moveBtns, warningLabel, btnRow);

        Scene scene = new Scene(root, 540, 520);
        if (primaryStage.getScene() != null)
            scene.getStylesheets().addAll(primaryStage.getScene().getStylesheets());
        dlg.setScene(scene);
        dlg.showAndWait();
    }

    // ─── Edit Plan Helpers ────────────────────────────────────────────────

    private void refreshEditList(ListView<String> lv, List<List<String>> termIds) {
        lv.getItems().clear();
        for (int t = 0; t < termIds.size(); t++) {
            for (String id : termIds.get(t)) {
                Course c = graph.getCourse(id);
                String label = String.format("Term %d  |  %s - %s  (%.1f cr)",
                        t + 1,
                        c != null ? c.getCode() : id,
                        c != null ? c.getTitle() : "?",
                        c != null ? c.getCredits() : 0.0);
                if (c != null && !allPrereqsCompleted(c)) label += "  [PREREQ INCOMPLETE]";
                lv.getItems().add(label);
            }
        }
    }

    /** Converts a flat list index to [termIndex, courseIndexInTerm]. */
    private int[] flatIndexToTermCourse(List<List<String>> termIds, int flatIdx) {
        int counter = 0;
        for (int t = 0; t < termIds.size(); t++) {
            for (int c = 0; c < termIds.get(t).size(); c++) {
                if (counter == flatIdx) return new int[]{t, c};
                counter++;
            }
        }
        return null;
    }

    private String checkPrereqWarnings(List<List<String>> termIds) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < termIds.size(); t++) {
            for (String id : termIds.get(t)) {
                Course c = graph.getCourse(id);
                if (c != null && !allPrereqsCompleted(c)) {
                    sb.append("Term ").append(t + 1).append(": ").append(c.getCode())
                      .append(" has prerequisites not yet marked Completed.\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private List<SemesterPlan> buildPlanFromIds(List<List<String>> termIds) {
        List<SemesterPlan> result = new ArrayList<>();
        for (int t = 0; t < termIds.size(); t++) {
            List<Course> courses = termIds.get(t).stream()
                    .map(graph::getCourse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!courses.isEmpty()) {
                result.add(new SemesterPlan(t + 1, courses));
            }
        }
        return result;
    }

    // ─── Export ───────────────────────────────────────────────────────────

    private void exportScheduleReport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Course Sequence Schedule");
        fc.setInitialFileName("Course_Sequence_Schedule.md");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Document", "*.md"));

        File file = fc.showSaveDialog(primaryStage);
        if (file == null) return;

        try (PrintWriter w = new PrintWriter(file)) {
            w.println("# Course Prerequisite Sequence Roadmap");
            w.println("Generated by PreRequix Planner");
            w.println("Max Credit Hours Per Term: " + creditSlider.getValue());
            w.println();
            for (SemesterPlan plan : lastPlan) {
                w.println("## Term " + plan.getTermNumber() +
                          " (" + plan.getTotalCredits() + " Credits)");
                for (Course c : plan.getCourses()) {
                    w.println(String.format("- **%s**: %s (%.1f credits, %s)",
                            c.getCode(), c.getTitle(), c.getCredits(), c.getDepartment()));
                }
                w.println();
            }
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.initOwner(primaryStage);
            ok.setTitle("Report Exported");
            ok.setHeaderText("Schedule Report Saved Successfully!");
            ok.setContentText("File saved to: " + file.getAbsolutePath());
            ok.showAndWait();
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(primaryStage);
            err.setTitle("Export Error");
            err.setHeaderText("Failed to export schedule report");
            err.setContentText(ex.getMessage());
            err.showAndWait();
        }
    }
}