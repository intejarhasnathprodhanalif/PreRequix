package com.prerequix.ui;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.concurrent.GraphComputeTask;
import com.prerequix.model.AcademicPlanResult;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.SemesterPlan;
import javafx.application.Platform;
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
 * Visual term-by-term academic sequence planner.
 *
 * <p>Academic constraints (hard rules):
 * <ul>
 *   <li>Each term has <b>exactly 5 courses</b> (last term may have fewer if
 *       the credit cap is reached).</li>
 *   <li>Total credits across all planned terms must <b>not exceed 120</b>.</li>
 *   <li>Courses that cannot fit within the 120-credit cap are shown in an
 *       "Excluded from Plan" panel.</li>
 * </ul>
 */
public class SequencePlannerPane extends BorderPane {

    /** Hard academic rules displayed in the UI and enforced in the algorithm. */
    private static final int    COURSES_PER_TERM  = GraphComputeTask.COURSES_PER_TERM;
    private static final double MAX_TOTAL_CREDITS = GraphComputeTask.MAX_TOTAL_CREDITS;

    private final Stage primaryStage;
    private final CourseGraph graph;
    private final Consumer<Course> onCourseSelectedListener;

    // ── Header widgets ────────────────────────────────────────────────────
    private final Label planSummaryLabel;
    private final Label creditUsageLabel;

    // ── Content area ──────────────────────────────────────────────────────
    private final VBox termsContainer;
    private final VBox excludedContainer;

    // ── Current plan state ────────────────────────────────────────────────
    private AcademicPlanResult lastResult = null;
    private boolean            lastHadCycle = false;

    public SequencePlannerPane(Stage primaryStage, CourseGraph graph,
                               Consumer<Course> onCourseSelectedListener) {
        this.primaryStage = primaryStage;
        this.graph = graph;
        this.onCourseSelectedListener = onCourseSelectedListener;

        setPadding(new Insets(16));

        // ── Header ────────────────────────────────────────────────────────
        VBox header = buildHeader();
        setTop(header);

        // ── Split center: planned terms on top, excluded below ────────────
        termsContainer   = new VBox(16);
        excludedContainer = new VBox(8);

        planSummaryLabel = new Label();
        planSummaryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

        creditUsageLabel = new Label();
        creditUsageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        VBox contentVBox = new VBox(20, planSummaryLabel, creditUsageLabel,
                termsContainer, buildExcludedSection());
        contentVBox.setPadding(new Insets(16, 0, 16, 0));

        ScrollPane scroll = new ScrollPane(contentVBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);

        generateRoadmap();
    }

    // ─── Header ───────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("card-panel");
        header.setPadding(new Insets(14, 18, 14, 18));

        Label title = new Label("Academic Course-Taking Sequence Planner");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitle = new Label(
                "Generates a prerequisite-respecting plan from your curriculum. " +
                "Not all courses need to be included — the plan stops at the credit cap.");
        subtitle.getStyleClass().add("muted-text");
        subtitle.setWrapText(true);

        // Constraint badges
        HBox badge1 = makeBadge("5 courses per term", "#dbeafe", "#1d4ed8");
        HBox badge2 = makeBadge("Max 120 total credits", "#dcfce7", "#15803d");
        HBox badge3 = makeBadge("Prerequisites always respected", "#fef9c3", "#a16207");

        HBox badges = new HBox(10, badge1, badge2, badge3);
        badges.setAlignment(Pos.CENTER_LEFT);

        // Action buttons
        Button editBtn = new Button("Edit Plan");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> openEditPlanDialog());

        Button exportBtn = new Button("Export Schedule");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setOnAction(e -> exportScheduleReport());

        HBox actions = new HBox(10, new Region(), editBtn, exportBtn);
        HBox.setHgrow(actions.getChildren().get(0), Priority.ALWAYS);

        HBox topRow = new HBox(10, badges, actions);
        HBox.setHgrow(topRow.getChildren().get(0), Priority.ALWAYS);
        topRow.setAlignment(Pos.CENTER_LEFT);

        header.getChildren().addAll(title, subtitle, new Separator(), topRow);
        return header;
    }

    private HBox makeBadge(String text, String bg, String fg) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + fg + ";");
        HBox box = new HBox(lbl);
        box.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 20px; -fx-padding: 3px 10px;");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox buildExcludedSection() {
        Label header = new Label("Excluded from Plan (credit cap reached)");
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #b45309;");

        VBox section = new VBox(8, header, excludedContainer);
        section.setStyle(
                "-fx-background-color: #fffbeb; -fx-background-radius: 8px; " +
                "-fx-border-color: #fcd34d; -fx-border-width: 1px; " +
                "-fx-border-radius: 8px; -fx-padding: 12px;");
        section.setVisible(false);
        section.setManaged(false);
        // Keep the outer reference so renderPlan() can show/hide it
        section.setUserData("excluded-section");
        excludedContainer.setUserData(section);   // cross-ref so we can toggle it
        return section;
    }

    // ─── Public API ───────────────────────────────────────────────────────

    /** Kept for compilation compatibility; the slider no longer exists. */
    public double getCurrentMaxCredits() { return MAX_TOTAL_CREDITS; }

    /** Called by MainController after a successful GraphComputeTask. */
    public void applyComputedPlan(AcademicPlanResult result, boolean hasCycle) {
        this.lastResult   = result;
        this.lastHadCycle = hasCycle;
        renderPlan(result, hasCycle);
    }

    /** Legacy overload used by the edit-plan dialog. */
    public void applyComputedPlan(List<SemesterPlan> schedule, boolean hasCycle) {
        applyComputedPlan(new AcademicPlanResult(schedule, List.of()), hasCycle);
    }

    /** Submits a background task to recompute and re-render the plan. */
    public void generateRoadmap() {
        GraphComputeTask task = new GraphComputeTask(graph);
        task.setOnSucceeded(e -> Platform.runLater(() ->
                applyComputedPlan(task.getValue().academicPlan, task.getValue().hasCycle())));
        task.setOnFailed(e -> Platform.runLater(() -> showError(task.getException().getMessage())));
        AppExecutor.getInstance().computePool().submit(task);
    }

    // ─── Rendering ────────────────────────────────────────────────────────

    private void renderPlan(AcademicPlanResult result, boolean hasCycle) {
        termsContainer.getChildren().clear();
        excludedContainer.getChildren().clear();

        if (hasCycle) {
            planSummaryLabel.setText("Cannot generate plan: circular dependency detected!");
            planSummaryLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ef4444;");
            creditUsageLabel.setText("");
            hideExcludedSection();
            return;
        }
        if (result == null || result.plannedTerms.isEmpty()) {
            planSummaryLabel.setText("All courses in your curriculum are completed!");
            planSummaryLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
            creditUsageLabel.setText("");
            hideExcludedSection();
            return;
        }

        // Summary bar
        planSummaryLabel.setText(String.format(
                "%d Term%s Required  |  %d Courses Planned",
                result.totalTerms,
                result.totalTerms != 1 ? "s" : "",
                result.plannedTerms.stream().mapToInt(p -> p.getCourses().size()).sum()));
        planSummaryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

        creditUsageLabel.setText(String.format(
                "Total Credits: %.1f / %.0f cap  |  %d courses excluded due to credit cap",
                result.totalPlannedCredits, MAX_TOTAL_CREDITS,
                result.excludedCourses.size()));

        // Term cards grid
        FlowPane grid = new FlowPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (SemesterPlan plan : result.plannedTerms) {
            grid.getChildren().add(createTermCard(plan));
        }
        termsContainer.getChildren().add(grid);

        // Excluded courses
        if (!result.excludedCourses.isEmpty()) {
            for (Course c : result.excludedCourses) {
                excludedContainer.getChildren().add(createExcludedRow(c));
            }
            showExcludedSection();
        } else {
            hideExcludedSection();
        }
    }

    private VBox createTermCard(SemesterPlan plan) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPrefWidth(310);

        // Header row
        Label termLabel = new Label("Semester / Term " + plan.getTermNumber());
        termLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        int coursesInTerm = plan.getCourses().size();
        Label countBadge = new Label(coursesInTerm + " / " + COURSES_PER_TERM + " courses");
        countBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #64748b;");

        Label creditBadge = new Label(String.format("%.1f cr", plan.getTotalCredits()));
        creditBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #3b82f6;");

        HBox top = new HBox(8, termLabel, new Region(), countBadge, creditBadge);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);

        // Fill progress: courses added vs target 5
        ProgressBar progress = new ProgressBar((double) coursesInTerm / COURSES_PER_TERM);
        progress.setMaxWidth(Double.MAX_VALUE);
        String barColor = coursesInTerm < COURSES_PER_TERM ? "#f59e0b" : "#3b82f6";
        progress.setStyle("-fx-accent: " + barColor + ";");

        // Course rows
        VBox courses = new VBox(5);
        for (Course c : plan.getCourses()) {
            courses.getChildren().add(createCourseRow(c));
        }

        card.getChildren().addAll(top, progress, new Separator(), courses);
        return card;
    }

    private HBox createCourseRow(Course c) {
        boolean prereqsMet = allPrereqsCompleted(c);

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 8, 5, 8));
        row.setStyle("-fx-background-color: " + (prereqsMet ? "#f1f5f9" : "#fff7ed") +
                     "; -fx-background-radius: 6px; -fx-cursor: hand;");

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
                    "Not all prerequisites of " + c.getCode() + " are Completed yet."));
        }

        row.setOnMouseClicked(e -> {
            if (onCourseSelectedListener != null) onCourseSelectedListener.accept(c);
        });
        return row;
    }

    private HBox createExcludedRow(Course c) {
        Label codeLabel = new Label(c.getCode());
        codeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #92400e;");
        codeLabel.setMinWidth(90);

        Label titleLabel = new Label(c.getTitle());
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #b45309;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label creditLabel = new Label(String.format("%.1f cr", c.getCredits()));
        creditLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #92400e; -fx-font-weight: 600;");

        Label reasonLabel = new Label("120 cr cap reached");
        reasonLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #d97706; " +
                "-fx-background-color: #fef3c7; -fx-background-radius: 10px; -fx-padding: 2px 7px;");

        HBox row = new HBox(8, codeLabel, titleLabel, creditLabel, reasonLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.setStyle("-fx-background-color: #fefce8; -fx-background-radius: 5px;");
        row.setOnMouseClicked(e -> {
            if (onCourseSelectedListener != null) onCourseSelectedListener.accept(c);
        });
        return row;
    }

    private void showError(String msg) {
        termsContainer.getChildren().clear();
        Label err = new Label("Error: " + msg);
        err.getStyleClass().add("conflict-text");
        termsContainer.getChildren().add(err);
        planSummaryLabel.setText("Plan generation failed");
        hideExcludedSection();
    }

    private void showExcludedSection() {
        Object ref = excludedContainer.getUserData();
        if (ref instanceof VBox section) {
            section.setVisible(true);
            section.setManaged(true);
        }
    }

    private void hideExcludedSection() {
        Object ref = excludedContainer.getUserData();
        if (ref instanceof VBox section) {
            section.setVisible(false);
            section.setManaged(false);
        }
    }

    private boolean allPrereqsCompleted(Course c) {
        return graph.getDirectPrerequisites(c.getId()).stream()
                .allMatch(Course::isCompleted);
    }

    // ─── Edit Plan Dialog ─────────────────────────────────────────────────

    private void openEditPlanDialog() {
        if (lastHadCycle) {
            alert(Alert.AlertType.WARNING, "Cannot Edit Plan",
                    "Resolve circular prerequisites before editing the plan.", null);
            return;
        }
        if (lastResult == null || lastResult.plannedTerms.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Nothing to Edit",
                    "All courses completed or no plan generated yet.", null);
            return;
        }

        // Mutable copy: termIndex -> list of course IDs
        List<List<String>> termIds = lastResult.plannedTerms.stream()
                .map(p -> p.getCourses().stream().map(Course::getId).collect(Collectors.toList()))
                .collect(Collectors.toList());

        Stage dlg = new Stage();
        dlg.initOwner(primaryStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Semester Plan");

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));

        Label hdr = new Label("Edit Semester Plan");
        hdr.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label hint = new Label(
                "Select a course and move it between terms.\n" +
                "Each term should have at most " + COURSES_PER_TERM + " courses.\n" +
                "Courses with unmet prerequisites are highlighted with [PREREQ INCOMPLETE].");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        ListView<String> listView = new ListView<>();
        listView.setPrefHeight(340);
        refreshEditList(listView, termIds);

        Label warnLabel = new Label();
        warnLabel.setWrapText(true);
        warnLabel.setStyle("-fx-text-fill: #f97316; -fx-font-weight: bold; -fx-font-size: 12px;");

        Button upBtn   = new Button("Move to Earlier Term");
        upBtn.getStyleClass().add("btn-secondary");
        Button downBtn = new Button("Move to Later Term");
        downBtn.getStyleClass().add("btn-secondary");

        upBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            int[] pos = flatIndexToTermCourse(termIds, sel);
            if (pos == null || pos[0] == 0) return;
            String id = termIds.get(pos[0]).remove(pos[1]);
            termIds.get(pos[0] - 1).add(id);
            termIds.removeIf(List::isEmpty);
            refreshEditList(listView, termIds);
            warnLabel.setText(checkPrereqWarnings(termIds));
        });

        downBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            int[] pos = flatIndexToTermCourse(termIds, sel);
            if (pos == null) return;
            if (pos[0] >= termIds.size() - 1) termIds.add(new ArrayList<>());
            String id = termIds.get(pos[0]).remove(pos[1]);
            termIds.get(pos[0] + 1).add(id);
            termIds.removeIf(List::isEmpty);
            refreshEditList(listView, termIds);
            warnLabel.setText(checkPrereqWarnings(termIds));
        });

        HBox moveBtns = new HBox(10, upBtn, downBtn);
        moveBtns.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Apply Plan");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            String warnings = checkPrereqWarnings(termIds);
            if (!warnings.isEmpty()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.initOwner(dlg);
                confirm.setTitle("Prerequisites Not Fully Met");
                confirm.setHeaderText("Some courses have incomplete prerequisites.");
                confirm.setContentText(warnings + "\n\nApply anyway?");
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) return;
            }
            List<SemesterPlan> edited = buildPlanFromIds(termIds);
            applyComputedPlan(edited, false);
            dlg.close();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> dlg.close());

        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(hdr, hint, listView, moveBtns, warnLabel, btnRow);
        Scene scene = new Scene(root, 560, 540);
        if (primaryStage.getScene() != null)
            scene.getStylesheets().addAll(primaryStage.getScene().getStylesheets());
        dlg.setScene(scene);
        dlg.showAndWait();
    }

    // ─── Edit Plan helpers ────────────────────────────────────────────────

    private void refreshEditList(ListView<String> lv, List<List<String>> termIds) {
        lv.getItems().clear();
        for (int t = 0; t < termIds.size(); t++) {
            for (String id : termIds.get(t)) {
                Course c = graph.getCourse(id);
                String entry = String.format("Term %d  |  %s - %s  (%.1f cr)",
                        t + 1,
                        c != null ? c.getCode() : id,
                        c != null ? c.getTitle() : "?",
                        c != null ? c.getCredits() : 0.0);
                if (c != null && !allPrereqsCompleted(c)) entry += "  [PREREQ INCOMPLETE]";
                lv.getItems().add(entry);
            }
        }
    }

    private int[] flatIndexToTermCourse(List<List<String>> termIds, int flatIdx) {
        int counter = 0;
        for (int t = 0; t < termIds.size(); t++)
            for (int c = 0; c < termIds.get(t).size(); c++)
                if (counter++ == flatIdx) return new int[]{t, c};
        return null;
    }

    private String checkPrereqWarnings(List<List<String>> termIds) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < termIds.size(); t++) {
            for (String id : termIds.get(t)) {
                Course c = graph.getCourse(id);
                if (c != null && !allPrereqsCompleted(c)) {
                    sb.append("Term ").append(t + 1).append(": ").append(c.getCode())
                      .append(" — prerequisites not yet Completed.\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private List<SemesterPlan> buildPlanFromIds(List<List<String>> termIds) {
        List<SemesterPlan> result = new ArrayList<>();
        for (int t = 0; t < termIds.size(); t++) {
            List<Course> courses = termIds.get(t).stream()
                    .map(graph::getCourse).filter(Objects::nonNull).collect(Collectors.toList());
            if (!courses.isEmpty()) result.add(new SemesterPlan(t + 1, courses));
        }
        return result;
    }

    // ─── Export ───────────────────────────────────────────────────────────

    private void exportScheduleReport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Academic Schedule");
        fc.setInitialFileName("Academic_Course_Schedule.md");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Document", "*.md"));

        File file = fc.showSaveDialog(primaryStage);
        if (file == null) return;

        try (PrintWriter w = new PrintWriter(file)) {
            w.println("# Academic Course Sequence Plan");
            w.println("Generated by PreRequix Planner");
            w.println("Rules: " + COURSES_PER_TERM + " courses per term | Max " +
                      (int) MAX_TOTAL_CREDITS + " total credits");
            w.println();
            if (lastResult != null) {
                for (SemesterPlan plan : lastResult.plannedTerms) {
                    w.println("## Term " + plan.getTermNumber() +
                              " (" + plan.getCourses().size() + " courses, " +
                              plan.getTotalCredits() + " credits)");
                    for (Course c : plan.getCourses()) {
                        w.println(String.format("- **%s**: %s (%.1f credits, %s)",
                                c.getCode(), c.getTitle(), c.getCredits(), c.getDepartment()));
                    }
                    w.println();
                }
                if (!lastResult.excludedCourses.isEmpty()) {
                    w.println("## Excluded from Plan (120 credit cap)");
                    for (Course c : lastResult.excludedCourses) {
                        w.println(String.format("- **%s**: %s (%.1f credits)",
                                c.getCode(), c.getTitle(), c.getCredits()));
                    }
                }
            }
            alert(Alert.AlertType.INFORMATION, "Report Exported",
                    "Schedule Report Saved Successfully!",
                    "File saved to: " + file.getAbsolutePath());
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Export Error", "Failed to export schedule report", ex.getMessage());
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────────

    private void alert(Alert.AlertType type, String title, String header, String content) {
        Alert a = new Alert(type);
        a.initOwner(primaryStage);
        a.setTitle(title);
        a.setHeaderText(header);
        if (content != null) a.setContentText(content);
        a.showAndWait();
    }
}