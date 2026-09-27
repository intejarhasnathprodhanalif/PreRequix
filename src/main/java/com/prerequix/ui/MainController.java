package com.prerequix.ui;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.concurrent.GraphComputeTask;
import com.prerequix.concurrent.StudentLoadTask;
import com.prerequix.concurrent.StudentSaveTask;
import com.prerequix.db.CurriculumDataLoader;
import com.prerequix.db.DatabaseManager;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.User;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main application layout controller. Accepts the authenticated {@link User}
 * and provides a fully personalised experience: loads the student's own
 * course plan from the database on startup, saves changes back on every edit.
 *
 * <p><b>Layout Responsiveness:</b> sidebar and detail-pane widths are bound to
 * the stage width via JavaFX property expressions.
 */
public class MainController extends BorderPane {

    private final Stage primaryStage;
    private final User  currentUser;
    private final CourseGraph graph = new CourseGraph();

    private final Label totalCoursesVal = new Label("...");
    private final Label completedVal    = new Label("...");
    private final Label availableVal    = new Label("...");
    private final Label totalCreditsVal = new Label("...");

    // ── Sidebar progress widgets ───────────────────────────────────────────
    private final ProgressBar sidebarProgress      = new ProgressBar(0);
    private final Label       sidebarProgressLabel  = new Label("0 / 0 courses  ·  0 / 120 cr");
    private       Label       sidebarNameLabel;

    private final Button graphNavBtn    = new Button("Graph Network");
    private final Button catalogNavBtn  = new Button("Course Catalog");
    private final Button sequenceNavBtn = new Button("Sequence Planner");

    private final StackPane centerContentStack = new StackPane();
    private final ConflictAlertPane conflictAlertPane = new ConflictAlertPane();
    private final CourseDetailPane courseDetailPane;

    private GraphViewPane       graphViewPane;
    private CourseCatalogPane   courseCatalogPane;
    private SequencePlannerPane sequencePlannerPane;

    private Label             statusBarLabel;
    private ProgressIndicator busySpinner;
    private boolean           isDarkMode = false;
    private final Runnable    onLogout;

    public MainController(Stage primaryStage, User user, Runnable onLogout) {
        this.primaryStage = primaryStage;
        this.currentUser  = user;
        this.onLogout     = onLogout;

        courseDetailPane = new CourseDetailPane(
                graph,
                this::onCourseStatusChanged,
                this::onCourseSelectedFromDetail,
                this::openEditCourseDialog);

        setTop(createHeaderBar());

        // Responsive sidebar (16 % of window width, clamped 160-240 px)
        VBox sidebar = createSidebar();
        sidebar.prefWidthProperty().bind(
                Bindings.max(160, Bindings.min(240, primaryStage.widthProperty().multiply(0.16))));
        setLeft(sidebar);

        // Responsive detail pane (26 % of window width, clamped 280-380 px)
        courseDetailPane.prefWidthProperty().bind(
                Bindings.max(280, Bindings.min(380, primaryStage.widthProperty().multiply(0.26))));
        setRight(courseDetailPane);

        VBox centerBox = new VBox(0, conflictAlertPane, centerContentStack);
        VBox.setVgrow(centerContentStack, Priority.ALWAYS);
        centerBox.setFillWidth(true);
        setCenter(centerBox);
        setBottom(createStatusBar());

        initializeViews();
        showGraphView();

        // Seed curricula data, then load the student's plan
        seedAndLoad();
    }

    // ─── Seed + load ──────────────────────────────────────────────────────

    private void seedAndLoad() {
        busySpinner.setVisible(true);
        statusBarLabel.setText("Initialising curricula data...");

        // Run seeding + curriculum check on background thread
        AppExecutor.getInstance().ioExecutor().submit(() -> {
            try {
                CurriculumDataLoader.seed(DatabaseManager.getInstance().getDbUrl());
            } catch (Exception e) {
                System.err.println("[Seed] " + e.getMessage());
            }

            // Check if student has a curriculum
            String currId = null;
            try {
                currId = DatabaseManager.getInstance().studentRepository()
                        .getStudentCurriculumId(currentUser.getStudentId());
            } catch (Exception e) {
                System.err.println("[CurrCheck] " + e.getMessage());
            }

            final String finalCurrId = currId;
            Platform.runLater(() -> {
                if (finalCurrId == null) {
                    // First login: show curriculum picker
                    busySpinner.setVisible(false);
                    showCurriculumPicker();
                } else {
                    // Returning student: load their saved plan
                    loadStudentPlan();
                }
            });
        });
    }

    /** Shows the curriculum picker dialog; after selection, loads the plan. */
    private void showCurriculumPicker() {
        CurriculumPickerDialog picker = new CurriculumPickerDialog(primaryStage, currentUser.getStudentId());
        picker.showAndWait();
        if (picker.getChosenCurriculumId() != null) {
            loadStudentPlan();
        } else {
            statusBarLabel.setText("No curriculum selected. Use 'Curriculum' button to choose.");
        }
    }

    private void loadStudentPlan() {
        busySpinner.setVisible(true);
        StudentLoadTask task = new StudentLoadTask(currentUser.getStudentId(), graph);
        task.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            refreshAllViews();
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Load failed: " + task.getException().getMessage());
        }));
        AppExecutor.getInstance().ioExecutor().submit(task);
    }

    // ─── Header ───────────────────────────────────────────────────────────

    private HBox createHeaderBar() {
        HBox header = new HBox(16);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("PreRequix");
        logo.getStyleClass().add("app-title");
        Label tag  = new Label("Course Prerequisite Planner");
        tag.getStyleClass().add("muted-text");
        VBox brandBox = new VBox(2, logo, tag);

        HBox statsBox = new HBox(10,
                createStatPill("TOTAL COURSES", totalCoursesVal),
                createStatPill("COMPLETED",     completedVal),
                createStatPill("AVAILABLE NOW", availableVal),
                createStatPill("TOTAL CREDITS", totalCreditsVal));
        statsBox.setAlignment(Pos.CENTER_LEFT);

        // Dark / Light toggle
        Button themeBtn = new Button("Dark Mode");
        themeBtn.getStyleClass().add("btn-secondary");
        themeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            var scene = primaryStage.getScene();
            if (scene != null) {
                if (isDarkMode) { scene.getRoot().getStyleClass().add("dark-theme");    themeBtn.setText("Light Mode"); }
                else            { scene.getRoot().getStyleClass().remove("dark-theme"); themeBtn.setText("Dark Mode"); }
            }
        });

        // Import from Web
        Button importWebBtn = new Button("Import from Web");
        importWebBtn.getStyleClass().add("btn-secondary");
        importWebBtn.setOnAction(e -> onImportFromWeb());

        // 📚 Curriculum button (replaces "+ New Course" blue button)
        Button curriculumBtn = new Button("📚 Curriculum");
        curriculumBtn.getStyleClass().add("btn-primary");
        curriculumBtn.setTooltip(new Tooltip("Switch or reset your curriculum"));
        curriculumBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(primaryStage);
            confirm.setTitle("Change Curriculum");
            confirm.setHeaderText("Switch to a different curriculum?");
            confirm.setContentText(
                "This will open the curriculum picker.\n" +
                "Your current progress will be saved first.\n\n" +
                "After choosing, the new curriculum will be added on top of your existing plan.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    saveStateAsync();
                    showCurriculumPicker();
                }
            });
        });

        HBox actionsBox = new HBox(8, importWebBtn, themeBtn, curriculumBtn);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(brandBox, new Separator(), statsBox, new Region(), actionsBox);
        HBox.setHgrow(header.getChildren().get(3), Priority.ALWAYS);
        return header;
    }

    private VBox createStatPill(String title, Label valLabel) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("stat-label");
        valLabel.getStyleClass().add("stat-value");
        VBox box = new VBox(2, titleLbl, valLabel);
        box.getStyleClass().add("stat-box");
        return box;
    }

    // ─── Sidebar ──────────────────────────────────────────────────────────

    private VBox createSidebar() {
        // ── User info ──────────────────────────────────────────────────────
        sidebarNameLabel = new Label(currentUser.getFullName());
        sidebarNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
        sidebarNameLabel.setWrapText(true);
        Label sid = new Label(currentUser.getStudentId());
        sid.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

        // ⚙ Settings button next to student name
        Button settingsBtn = new Button("⚙");
        settingsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; " +
                             "-fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4;");
        settingsBtn.setTooltip(new Tooltip("Account Settings"));
        settingsBtn.setOnAction(e -> {
            ProfileSettingsDialog dlg = new ProfileSettingsDialog(
                    primaryStage, currentUser, newName -> {
                        sidebarNameLabel.setText(newName);
                        primaryStage.setTitle("PreRequix  -  " + newName +
                                              " (" + currentUser.getStudentId() + ")");
                    });
            dlg.showAndWait();
        });

        HBox nameRow = new HBox(4, sidebarNameLabel, new Region(), settingsBtn);
        HBox.setHgrow(nameRow.getChildren().get(1), Priority.ALWAYS);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        VBox userInfo = new VBox(2, nameRow, sid);
        userInfo.setPadding(new Insets(0, 0, 10, 4));
        userInfo.setStyle("-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        // ── Nav buttons ────────────────────────────────────────────────────
        graphNavBtn.getStyleClass().addAll("sidebar-btn", "sidebar-btn-active");
        catalogNavBtn.getStyleClass().add("sidebar-btn");
        sequenceNavBtn.getStyleClass().add("sidebar-btn");

        for (Button b : new Button[]{graphNavBtn, catalogNavBtn, sequenceNavBtn})
            b.setMaxWidth(Double.MAX_VALUE);

        graphNavBtn.setOnAction(e    -> showGraphView());
        catalogNavBtn.setOnAction(e  -> showCatalogView());
        sequenceNavBtn.setOnAction(e -> showSequenceView());

        // ── Progress section ───────────────────────────────────────────────
        Label progressTitle = new Label("Curriculum Progress");
        progressTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; " +
                               "-fx-text-fill: #64748b; -fx-padding: 0 0 2 0;");

        sidebarProgress.setMaxWidth(Double.MAX_VALUE);
        sidebarProgress.setStyle("-fx-accent: #3b82f6;");
        sidebarProgress.setPrefHeight(8);

        sidebarProgressLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        sidebarProgressLabel.setWrapText(true);

        VBox progressBox = new VBox(4, progressTitle, sidebarProgress, sidebarProgressLabel);
        progressBox.setPadding(new Insets(8, 4, 0, 4));
        progressBox.setStyle("-fx-border-color: #e2e8f0 transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ── Sign Out button (bottom of sidebar) ───────────────────────────────
        Button signOutBtn = new Button("🔒  Sign Out");
        signOutBtn.setMaxWidth(Double.MAX_VALUE);
        signOutBtn.setStyle(
            "-fx-background-color: #fff1f2; -fx-text-fill: #e11d48;" +
            "-fx-font-weight: bold; -fx-font-size: 12px;" +
            "-fx-padding: 9 12; -fx-background-radius: 7; -fx-cursor: hand;" +
            "-fx-border-color: #fecdd3; -fx-border-width: 1; -fx-border-radius: 7;");
        signOutBtn.setOnMouseEntered(e -> signOutBtn.setStyle(signOutBtn.getStyle()
            .replace("#fff1f2", "#ffe4e6")));
        signOutBtn.setOnMouseExited(e -> signOutBtn.setStyle(signOutBtn.getStyle()
            .replace("#ffe4e6", "#fff1f2")));
        signOutBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(primaryStage);
            confirm.setTitle("Sign Out");
            confirm.setHeaderText("Sign out of your account?");
            confirm.setContentText(
                "Your plan will be saved automatically.\n" +
                "You can sign back in any time.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    saveStateAsync();          // save before leaving
                    if (onLogout != null) Platform.runLater(onLogout);
                }
            });
        });

        VBox sidebar = new VBox(10, userInfo, graphNavBtn, catalogNavBtn, sequenceNavBtn,
                               spacer, progressBox, signOutBtn);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 10, 16, 10));
        return sidebar;
    }

    // ─── Views ────────────────────────────────────────────────────────────

    private void initializeViews() {
        graphViewPane       = new GraphViewPane(graph, this::onCourseSelectedFromView);
        courseCatalogPane   = new CourseCatalogPane(primaryStage, graph,
                                  this::onCourseSelectedFromView, this::onGraphDataUpdated);
        sequencePlannerPane = new SequencePlannerPane(primaryStage, graph,
                                  this::onCourseSelectedFromView);
        sequencePlannerPane.setOnPlanEdited(this::saveStateAsync);

        for (var pane : new javafx.scene.Node[]{graphViewPane, courseCatalogPane, sequencePlannerPane}) {
            pane.setVisible(false); ((javafx.scene.layout.Pane) pane).setManaged(false);
        }
        centerContentStack.getChildren().addAll(graphViewPane, courseCatalogPane, sequencePlannerPane);
    }

    private void showGraphView()    { setNavActive(graphNavBtn);    setOnlyVisible(graphViewPane);    graphViewPane.renderGraph(); }
    private void showCatalogView()  { setNavActive(catalogNavBtn);  setOnlyVisible(courseCatalogPane); courseCatalogPane.refreshTable(); }
    private void showSequenceView() { setNavActive(sequenceNavBtn); setOnlyVisible(sequencePlannerPane); sequencePlannerPane.generateRoadmap(); }

    private void setOnlyVisible(javafx.scene.Node target) {
        centerContentStack.getChildren().forEach(c -> { c.setVisible(c == target); ((Region) c).setManaged(c == target); });
    }

    private void setNavActive(Button active) {
        for (Button b : new Button[]{graphNavBtn, catalogNavBtn, sequenceNavBtn})
            b.getStyleClass().remove("sidebar-btn-active");
        active.getStyleClass().add("sidebar-btn-active");
    }

    // ─── Event handlers ───────────────────────────────────────────────────

    private void onCourseSelectedFromView(Course c)  { courseDetailPane.displayCourse(c); }
    private void onCourseSelectedFromDetail(Course c) { courseDetailPane.displayCourse(c); graphViewPane.selectCourse(c); }
    private void onCourseStatusChanged(Course c)      { saveStateAsync(); refreshAllViews(); }
    private void onGraphDataUpdated()                 { saveStateAsync(); refreshAllViews(); }

    private void openEditCourseDialog(Course course) {
        CourseDialog dialog = new CourseDialog(primaryStage, graph, course);
        dialog.showAndWait();
        if (dialog.isSaved()) { saveStateAsync(); refreshAllViews(); }
    }

    // ─── Import from Web ──────────────────────────────────────────────────

    private void onImportFromWeb() {
        statusBarLabel.setText("Fetching courses from GitHub...");
        busySpinner.setVisible(true);

        var fetchTask = new com.prerequix.concurrent.FetchCoursesTask(
                com.prerequix.net.CourseApiClient.SAMPLE_COURSES_URL);
        fetchTask.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        fetchTask.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            showImportDialog(fetchTask.getValue());
        }));
        fetchTask.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Fetch failed: " + fetchTask.getException().getMessage());
        }));
        AppExecutor.getInstance().computePool().submit(fetchTask);
    }

    private void showImportDialog(com.prerequix.concurrent.FetchCoursesTask.FetchResult result) {
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
        dlg.initOwner(primaryStage);
        dlg.setTitle("Import Courses from Web");
        dlg.setHeaderText("Fetched: " + result.sourceInfo());
        dlg.setContentText(result.courses().size() + " courses found.\nImport into your plan?");
        dlg.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                for (Course c : result.courses()) {
                    if (!graph.hasCourse(c.getId())) graph.addCourse(c);
                }
                for (Course c : result.courses())
                    for (String prereqCode : new java.util.HashSet<>(c.getPrerequisiteIds())) {
                        String prereqId = Course.sanitizeId(prereqCode);
                        if (graph.hasCourse(prereqId) && !graph.wouldCauseCycle(c.getId(), prereqId))
                            graph.addPrerequisite(c.getId(), prereqId);
                    }
                saveStateAsync();
                refreshAllViews();
            }
        });
    }

    // ─── Refresh + Save ───────────────────────────────────────────────────

    public void refreshAllViews() {
        graphViewPane.renderGraph();
        courseCatalogPane.refreshTable();

        GraphComputeTask task = new GraphComputeTask(graph);
        task.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            GraphComputeTask.Result r = task.getValue();
            totalCoursesVal.setText(String.valueOf(r.total));
            completedVal.setText(String.valueOf(r.completed));
            availableVal.setText(String.valueOf(r.available));
            totalCreditsVal.setText(String.format("%.1f", r.totalCredits));
            conflictAlertPane.applyComputedCycle(r.cyclePath);
            sequencePlannerPane.applyComputedPlan(r.academicPlan, r.hasCycle());
            // Update sidebar progress bar
            double pct = r.total > 0 ? (double) r.completed / r.total : 0;
            sidebarProgress.setProgress(pct);
            sidebarProgressLabel.setText(String.format(
                    "%d / %d courses  ·  %.0f / 120 cr",
                    r.completed, r.total, r.totalCredits));
            statusBarLabel.setText("Ready.");
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Compute error: " + task.getException().getMessage());
        }));
        busySpinner.setVisible(true);
        AppExecutor.getInstance().computePool().submit(task);
    }

    private void saveStateAsync() {
        StudentSaveTask save = new StudentSaveTask(currentUser.getStudentId(), graph);
        save.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        save.setOnFailed(e ->
                Platform.runLater(() -> statusBarLabel.setText(
                        "Save failed: " + save.getException().getMessage())));
        AppExecutor.getInstance().ioExecutor().submit(save);
    }

    // ─── Status bar ───────────────────────────────────────────────────────

    private HBox createStatusBar() {
        statusBarLabel = new Label("Initialising...");
        statusBarLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        busySpinner = new ProgressIndicator();
        busySpinner.setPrefSize(14, 14);
        busySpinner.setStyle("-fx-accent: #3b82f6;");
        busySpinner.setVisible(false);
        HBox bar = new HBox(8, busySpinner, statusBarLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 14, 4, 14));
        bar.setStyle("-fx-background-color: #f8fafc; " +
                     "-fx-border-color: #e2e8f0 transparent transparent transparent; " +
                     "-fx-border-width: 1px;");
        return bar;
    }
}