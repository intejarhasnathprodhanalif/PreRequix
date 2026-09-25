package com.prerequix.ui;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.concurrent.GraphComputeTask;
import com.prerequix.concurrent.LoadDataTask;
import com.prerequix.concurrent.SaveDataTask;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.storage.CourseStorageManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main application layout controller managing sidebar navigation, header status bar, views, and data persistence.
 */
public class MainController extends BorderPane {

    private final Stage primaryStage;
    private final CourseGraph graph;
    private final CourseStorageManager storageManager;

    private final Label totalCoursesVal;
    private final Label completedVal;
    private final Label availableVal;
    private final Label totalCreditsVal;

    private final Button graphNavBtn;
    private final Button catalogNavBtn;
    private final Button sequenceNavBtn;

    private final StackPane centerContentStack;
    private final ConflictAlertPane conflictAlertPane;
    private final CourseDetailPane courseDetailPane;

    private GraphViewPane graphViewPane;
    private CourseCatalogPane courseCatalogPane;
    private SequencePlannerPane sequencePlannerPane;

    private boolean isDarkMode = false;

    /** Shown at the bottom of the window; updated by background tasks. */
    private Label statusBarLabel;
    /** Spinner that spins whenever a background task is running. */
    private ProgressIndicator busySpinner;

    public MainController(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.graph = new CourseGraph();
        this.storageManager = new CourseStorageManager();

        // Initialize Stats Labels
        totalCoursesVal = new Label("…");
        completedVal    = new Label("…");
        availableVal    = new Label("…");
        totalCreditsVal = new Label("…");

        // Conflict Warning Banner
        conflictAlertPane = new ConflictAlertPane();

        // Right Inspector Pane
        courseDetailPane = new CourseDetailPane(
                graph,
                this::onCourseStatusChanged,
                this::onCourseSelectedFromDetail,
                this::openEditCourseDialog
        );

        // Sidebar Buttons
        graphNavBtn    = new Button("🕸️ Graph Network");
        catalogNavBtn  = new Button("📚 Course Catalog");
        sequenceNavBtn = new Button("🗓️ Sequence Planner");

        setupSidebarNavigation();

        // Top Header
        setTop(createHeaderBar());

        // Left Sidebar
        setLeft(createSidebar());

        // Right Detail Drawer
        setRight(courseDetailPane);

        // Center Views Stack
        centerContentStack = new StackPane();
        centerContentStack.setMaxWidth(Double.MAX_VALUE);
        centerContentStack.setMaxHeight(Double.MAX_VALUE);

        VBox centerBox = new VBox(0, conflictAlertPane, centerContentStack);
        VBox.setVgrow(centerContentStack, Priority.ALWAYS);
        centerBox.setFillWidth(true);

        setCenter(centerBox);
        setBottom(createStatusBar());

        initializeViews();
        showGraphView();

        // ── Load data asynchronously on the I/O thread ───────────────────
        LoadDataTask loadTask = new LoadDataTask(graph, storageManager);
        loadTask.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        loadTask.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            refreshAllViews();
        }));
        loadTask.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Load failed: " + loadTask.getException().getMessage());
            refreshAllViews();   // show whatever is in the graph already
        }));
        busySpinner.setVisible(true);
        AppExecutor.getInstance().ioExecutor().submit(loadTask);
    }

    private HBox createHeaderBar() {
        HBox header = new HBox(16);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("🎓 PreRequix");
        logo.getStyleClass().add("app-title");

        Label tag = new Label("Course Prerequisite Planner");
        tag.getStyleClass().add("muted-text");

        VBox brandBox = new VBox(2, logo, tag);

        // Stats Badges
        HBox statsBox = new HBox(10,
                createStatPill("TOTAL COURSES", totalCoursesVal),
                createStatPill("COMPLETED", completedVal),
                createStatPill("AVAILABLE NOW", availableVal),
                createStatPill("TOTAL CREDITS", totalCreditsVal)
        );
        statsBox.setAlignment(Pos.CENTER_LEFT);

        // Preset Curricula Menu
        ComboBox<String> presetCombo = new ComboBox<>();
        presetCombo.getItems().addAll(
                "📂 Load CS Curriculum",
                "⚡ Load EE Curriculum",
                "📊 Load Business Analytics"
        );
        presetCombo.setPromptText("Sample Curricula");
        presetCombo.getStyleClass().add("btn-secondary");
        presetCombo.setOnAction(e -> {
            String selected = presetCombo.getValue();
            if (selected != null) {
                if (selected.contains("CS")) storageManager.loadComputerSciencePreset(graph);
                else if (selected.contains("EE")) storageManager.loadElectricalEngineeringPreset(graph);
                else if (selected.contains("Business")) storageManager.loadBusinessAnalyticsPreset(graph);

                saveState();
                refreshAllViews();
            }
        });

        // Theme Switcher Button
        Button themeBtn = new Button("🌙 Dark");
        themeBtn.getStyleClass().add("btn-secondary");
        themeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            Scene scene = primaryStage.getScene();
            if (scene != null) {
                if (isDarkMode) {
                    scene.getRoot().getStyleClass().add("dark-theme");
                    themeBtn.setText("☀️ Light");
                } else {
                    scene.getRoot().getStyleClass().remove("dark-theme");
                    themeBtn.setText("🌙 Dark");
                }
            }
        });

        // Add Course Quick Button
        Button addCourseBtn = new Button("➕ New Course");
        addCourseBtn.getStyleClass().add("btn-primary");
        addCourseBtn.setOnAction(e -> {
            CourseDialog dialog = new CourseDialog(primaryStage, graph, null);
            dialog.showAndWait();
            if (dialog.isSaved()) {
                saveState();
                refreshAllViews();
            }
        });

        HBox actionsBox = new HBox(8, presetCombo, themeBtn, addCourseBtn);
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

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(200);

        graphNavBtn.getStyleClass().addAll("sidebar-btn", "sidebar-btn-active");
        catalogNavBtn.getStyleClass().add("sidebar-btn");
        sequenceNavBtn.getStyleClass().add("sidebar-btn");

        graphNavBtn.setMaxWidth(Double.MAX_VALUE);
        catalogNavBtn.setMaxWidth(Double.MAX_VALUE);
        sequenceNavBtn.setMaxWidth(Double.MAX_VALUE);

        graphNavBtn.setOnAction(e -> showGraphView());
        catalogNavBtn.setOnAction(e -> showCatalogView());
        sequenceNavBtn.setOnAction(e -> showSequenceView());

        sidebar.getChildren().addAll(graphNavBtn, catalogNavBtn, sequenceNavBtn);
        return sidebar;
    }

    private void setupSidebarNavigation() {
    }

    private void initializeViews() {
        graphViewPane = new GraphViewPane(graph, this::onCourseSelectedFromView);
        courseCatalogPane = new CourseCatalogPane(primaryStage, graph, this::onCourseSelectedFromView, this::onGraphDataUpdated);
        sequencePlannerPane = new SequencePlannerPane(primaryStage, graph, this::onCourseSelectedFromView);

        // Start with all hidden; showGraphView() will reveal the correct one
        graphViewPane.setVisible(false);     graphViewPane.setManaged(false);
        courseCatalogPane.setVisible(false); courseCatalogPane.setManaged(false);
        sequencePlannerPane.setVisible(false); sequencePlannerPane.setManaged(false);

        centerContentStack.getChildren().addAll(graphViewPane, courseCatalogPane, sequencePlannerPane);
    }

    /** Show only graphViewPane, hide the other two. */
    private void showGraphView() {
        setNavActive(graphNavBtn);
        setOnlyVisible(graphViewPane);
        graphViewPane.renderGraph();
    }

    /** Show only courseCatalogPane, hide the other two. */
    private void showCatalogView() {
        setNavActive(catalogNavBtn);
        setOnlyVisible(courseCatalogPane);
        courseCatalogPane.refreshTable();
    }

    /** Show only sequencePlannerPane, hide the other two. */
    private void showSequenceView() {
        setNavActive(sequenceNavBtn);
        setOnlyVisible(sequencePlannerPane);
        sequencePlannerPane.generateRoadmap();
    }

    /** Make exactly one child of centerContentStack visible; hide+unmanage all others. */
    private void setOnlyVisible(javafx.scene.Node target) {
        for (javafx.scene.Node child : centerContentStack.getChildren()) {
            boolean show = child == target;
            child.setVisible(show);
            child.setManaged(show);
        }
    }

    private void setNavActive(Button activeBtn) {
        graphNavBtn.getStyleClass().remove("sidebar-btn-active");
        catalogNavBtn.getStyleClass().remove("sidebar-btn-active");
        sequenceNavBtn.getStyleClass().remove("sidebar-btn-active");

        activeBtn.getStyleClass().add("sidebar-btn-active");
    }

    private void onCourseSelectedFromView(Course course) {
        courseDetailPane.displayCourse(course);
    }

    private void onCourseSelectedFromDetail(Course course) {
        courseDetailPane.displayCourse(course);
        graphViewPane.selectCourse(course);
    }

    private void onCourseStatusChanged(Course course) {
        saveState();
        refreshAllViews();
    }

    private void openEditCourseDialog(Course course) {
        CourseDialog dialog = new CourseDialog(primaryStage, graph, course);
        dialog.showAndWait();
        if (dialog.isSaved()) {
            saveState();
            refreshAllViews();
        }
    }

    private void onGraphDataUpdated() {
        saveStateAsync();
        refreshAllViews();
    }

    /**
     * Triggers a full async refresh:
     * 1. Re-renders the graph canvas (UI thread – fast).
     * 2. Runs {@link GraphComputeTask} off-thread to compute stats, cycle,
     *    and semester plan, then applies results back on the UI thread.
     * 3. Asks CourseCatalogPane to re-filter its table.
     */
    public void refreshAllViews() {
        // ── UI-thread work (fast): redraw graph nodes ────────────────────
        graphViewPane.renderGraph();
        courseCatalogPane.refreshTable();

        // ── Background compute task ──────────────────────────────────────
        double maxCredits = sequencePlannerPane.getCurrentMaxCredits();
        GraphComputeTask task = new GraphComputeTask(graph, maxCredits);

        task.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            GraphComputeTask.Result r = task.getValue();

            // Update header stats
            totalCoursesVal.setText(String.valueOf(r.total));
            completedVal.setText(String.valueOf(r.completed));
            availableVal.setText(String.valueOf(r.available));
            totalCreditsVal.setText(String.format("%.1f", r.totalCredits));

            // Update conflict banner
            conflictAlertPane.applyComputedCycle(r.cyclePath);

            // Push semester plan into the planner view
            sequencePlannerPane.applyComputedPlan(r.semesterPlan, r.hasCycle());

            statusBarLabel.setText("Ready.");
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Compute error: " + task.getException().getMessage());
        }));

        busySpinner.setVisible(true);
        AppExecutor.getInstance().computePool().submit(task);
    }

    /**
     * Saves the graph asynchronously on the dedicated I/O thread so the
     * UI never freezes during a file write.
     */
    private void saveStateAsync() {
        SaveDataTask saveTask = new SaveDataTask(graph, storageManager);
        saveTask.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        saveTask.setOnFailed(e ->
                Platform.runLater(() -> statusBarLabel.setText(
                        "Save failed: " + saveTask.getException().getMessage())));
        AppExecutor.getInstance().ioExecutor().submit(saveTask);
    }

    /** Thin status bar shown at the bottom of the window. */
    private HBox createStatusBar() {
        statusBarLabel = new Label("Initialising…");
        statusBarLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        busySpinner = new ProgressIndicator();
        busySpinner.setPrefSize(14, 14);
        busySpinner.setStyle("-fx-accent: #3b82f6;");
        busySpinner.setVisible(false);

        HBox bar = new HBox(8, busySpinner, statusBarLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 14, 4, 14));
        bar.setStyle("-fx-background-color: #f8fafc; "
                + "-fx-border-color: #e2e8f0 transparent transparent transparent; "
                + "-fx-border-width: 1px;");
        return bar;
    }
}
