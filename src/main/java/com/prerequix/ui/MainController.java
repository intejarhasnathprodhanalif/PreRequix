package com.prerequix.ui;

import com.prerequix.concurrent.AppExecutor;
import com.prerequix.concurrent.DatabaseLoadTask;
import com.prerequix.concurrent.DatabaseSaveTask;
import com.prerequix.concurrent.FetchCoursesTask;
import com.prerequix.concurrent.GraphComputeTask;
import com.prerequix.db.DatabaseManager;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main application layout controller.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Builds the global BorderPane layout (header, sidebar, center stack, detail pane, status bar).</li>
 *   <li>Manages view switching (Graph / Catalog / Sequence Planner) via a StackPane.</li>
 *   <li>Wires all async tasks: DB load/save, graph compute, and remote course fetch.</li>
 *   <li>Applies responsive layout bindings so sidebar and detail pane scale with the window.</li>
 * </ul>
 *
 * <p><b>Layout Responsiveness:</b> sidebar width and detail pane width are bound
 * to the stage width via JavaFX property expressions, so resizing the window
 * automatically reshapes every major panel.
 */
public class MainController extends BorderPane {

    private final Stage primaryStage;
    private final CourseGraph graph;

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

    private Label statusBarLabel;
    private ProgressIndicator busySpinner;

    public MainController(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.graph = new CourseGraph();

        totalCoursesVal = new Label("...");
        completedVal    = new Label("...");
        availableVal    = new Label("...");
        totalCreditsVal = new Label("...");

        conflictAlertPane = new ConflictAlertPane();

        courseDetailPane = new CourseDetailPane(
                graph,
                this::onCourseStatusChanged,
                this::onCourseSelectedFromDetail,
                this::openEditCourseDialog
        );

        graphNavBtn    = new Button("Graph Network");
        catalogNavBtn  = new Button("Course Catalog");
        sequenceNavBtn = new Button("Sequence Planner");

        setupSidebarNavigation();
        setTop(createHeaderBar());

        // ── Sidebar with responsive width binding ─────────────────────────
        VBox sidebar = createSidebar();
        // Width = 16 % of stage width, clamped between 160 and 240 px
        sidebar.prefWidthProperty().bind(
                Bindings.max(160, Bindings.min(240, primaryStage.widthProperty().multiply(0.16))));
        setLeft(sidebar);

        // ── Detail pane with responsive width binding ─────────────────────
        // Width = 26 % of stage width, clamped between 280 and 380 px
        courseDetailPane.prefWidthProperty().bind(
                Bindings.max(280, Bindings.min(380, primaryStage.widthProperty().multiply(0.26))));
        setRight(courseDetailPane);

        // ── Center view stack ─────────────────────────────────────────────
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

        // ── Load data from SQLite on start-up ─────────────────────────────
        DatabaseLoadTask loadTask = new DatabaseLoadTask(
                graph, DatabaseManager.getInstance().repository());
        loadTask.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        loadTask.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            refreshAllViews();
        }));
        loadTask.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("DB load failed: " + loadTask.getException().getMessage());
            refreshAllViews();
        }));
        busySpinner.setVisible(true);
        AppExecutor.getInstance().ioExecutor().submit(loadTask);
    }

    // ─── Header ───────────────────────────────────────────────────────────

    private HBox createHeaderBar() {
        HBox header = new HBox(16);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("PreRequix");
        logo.getStyleClass().add("app-title");
        Label tag = new Label("Course Prerequisite Planner");
        tag.getStyleClass().add("muted-text");
        VBox brandBox = new VBox(2, logo, tag);

        HBox statsBox = new HBox(10,
                createStatPill("TOTAL COURSES", totalCoursesVal),
                createStatPill("COMPLETED",     completedVal),
                createStatPill("AVAILABLE NOW", availableVal),
                createStatPill("TOTAL CREDITS", totalCreditsVal)
        );
        statsBox.setAlignment(Pos.CENTER_LEFT);

        // Preset curricula
        ComboBox<String> presetCombo = new ComboBox<>();
        presetCombo.getItems().addAll(
                "Load CS Curriculum",
                "Load EE Curriculum",
                "Load Business Analytics"
        );
        presetCombo.setPromptText("Sample Curricula");
        presetCombo.getStyleClass().add("btn-secondary");
        presetCombo.setOnAction(e -> {
            String sel = presetCombo.getValue();
            if (sel != null) {
                com.prerequix.storage.CourseStorageManager mgr =
                        new com.prerequix.storage.CourseStorageManager();
                if (sel.contains("CS"))       mgr.loadComputerSciencePreset(graph);
                else if (sel.contains("EE"))  mgr.loadElectricalEngineeringPreset(graph);
                else if (sel.contains("Business")) mgr.loadBusinessAnalyticsPreset(graph);
                saveStateAsync();
                refreshAllViews();
                presetCombo.setValue(null);
            }
        });

        // Import from Web
        Button importWebBtn = new Button("Import from Web");
        importWebBtn.getStyleClass().add("btn-secondary");
        importWebBtn.setOnAction(e -> onImportFromWeb());

        // Dark / Light toggle
        Button themeBtn = new Button("Dark Mode");
        themeBtn.getStyleClass().add("btn-secondary");
        themeBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            Scene scene = primaryStage.getScene();
            if (scene != null) {
                if (isDarkMode) {
                    scene.getRoot().getStyleClass().add("dark-theme");
                    themeBtn.setText("Light Mode");
                } else {
                    scene.getRoot().getStyleClass().remove("dark-theme");
                    themeBtn.setText("Dark Mode");
                }
            }
        });

        Button addCourseBtn = new Button("+ New Course");
        addCourseBtn.getStyleClass().add("btn-primary");
        addCourseBtn.setOnAction(e -> {
            CourseDialog dialog = new CourseDialog(primaryStage, graph, null);
            dialog.showAndWait();
            if (dialog.isSaved()) {
                saveStateAsync();
                refreshAllViews();
            }
        });

        HBox actionsBox = new HBox(8, presetCombo, importWebBtn, themeBtn, addCourseBtn);
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
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 10, 16, 10));

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

    private void setupSidebarNavigation() {}

    // ─── Views ────────────────────────────────────────────────────────────

    private void initializeViews() {
        graphViewPane      = new GraphViewPane(graph, this::onCourseSelectedFromView);
        courseCatalogPane  = new CourseCatalogPane(primaryStage, graph,
                                 this::onCourseSelectedFromView, this::onGraphDataUpdated);
        sequencePlannerPane = new SequencePlannerPane(primaryStage, graph,
                                 this::onCourseSelectedFromView);

        graphViewPane.setVisible(false);       graphViewPane.setManaged(false);
        courseCatalogPane.setVisible(false);   courseCatalogPane.setManaged(false);
        sequencePlannerPane.setVisible(false);  sequencePlannerPane.setManaged(false);

        centerContentStack.getChildren().addAll(
                graphViewPane, courseCatalogPane, sequencePlannerPane);
    }

    private void showGraphView() {
        setNavActive(graphNavBtn);
        setOnlyVisible(graphViewPane);
        graphViewPane.renderGraph();
    }

    private void showCatalogView() {
        setNavActive(catalogNavBtn);
        setOnlyVisible(courseCatalogPane);
        courseCatalogPane.refreshTable();
    }

    private void showSequenceView() {
        setNavActive(sequenceNavBtn);
        setOnlyVisible(sequencePlannerPane);
        sequencePlannerPane.generateRoadmap();
    }

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

    // ─── Event handlers ───────────────────────────────────────────────────

    private void onCourseSelectedFromView(Course course) {
        courseDetailPane.displayCourse(course);
    }

    private void onCourseSelectedFromDetail(Course course) {
        courseDetailPane.displayCourse(course);
        graphViewPane.selectCourse(course);
    }

    private void onCourseStatusChanged(Course course) {
        saveStateAsync();
        refreshAllViews();
    }

    private void openEditCourseDialog(Course course) {
        CourseDialog dialog = new CourseDialog(primaryStage, graph, course);
        dialog.showAndWait();
        if (dialog.isSaved()) {
            saveStateAsync();
            refreshAllViews();
        }
    }

    private void onGraphDataUpdated() {
        saveStateAsync();
        refreshAllViews();
    }

    // ─── Import from Web ──────────────────────────────────────────────────

    /**
     * Launches a background {@link FetchCoursesTask} that GETs the sample
     * courses JSON from GitHub, parses it, and opens an import-preview dialog.
     */
    private void onImportFromWeb() {
        statusBarLabel.setText("Fetching courses from GitHub...");
        busySpinner.setVisible(true);

        FetchCoursesTask fetchTask = new FetchCoursesTask(
                com.prerequix.net.CourseApiClient.SAMPLE_COURSES_URL);

        fetchTask.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));

        fetchTask.setOnSucceeded(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            FetchCoursesTask.FetchResult result = fetchTask.getValue();
            showImportDialog(result);
        }));

        fetchTask.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Fetch failed: " + fetchTask.getException().getMessage());
            new Alert(Alert.AlertType.ERROR,
                    "Could not fetch courses from GitHub:\n" +
                    fetchTask.getException().getMessage()).showAndWait();
        }));

        AppExecutor.getInstance().computePool().submit(fetchTask);
    }

    /** Shows a preview dialog; confirmed courses are imported into the graph + DB. */
    private void showImportDialog(FetchCoursesTask.FetchResult result) {
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
        dlg.initOwner(primaryStage);
        dlg.setTitle("Import Courses from Web");
        dlg.setHeaderText("Fetched from: " + result.sourceInfo());
        dlg.setContentText(
                result.courses().size() + " courses found in the remote JSON.\n\n" +
                "Import all of them into your curriculum?\n" +
                "(Existing courses with the same code will be updated.)");

        dlg.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                int imported = 0;
                for (Course c : result.courses()) {
                    if (!graph.hasCourse(c.getId())) {
                        graph.addCourse(c);
                    } else {
                        // Update existing entry
                        Course existing = graph.getCourse(c.getId());
                        existing.setTitle(c.getTitle());
                        existing.setCredits(c.getCredits());
                        existing.setDepartment(c.getDepartment());
                        existing.setDescription(c.getDescription());
                    }
                    imported++;
                }
                // Resolve prerequisite codes → IDs after all courses are in the graph
                for (Course c : result.courses()) {
                    for (String prereqCode : new java.util.HashSet<>(c.getPrerequisiteIds())) {
                        String prereqId = Course.sanitizeId(prereqCode);
                        if (graph.hasCourse(prereqId) &&
                                !graph.wouldCauseCycle(c.getId(), prereqId)) {
                            graph.addPrerequisite(c.getId(), prereqId);
                        }
                    }
                }
                saveStateAsync();
                refreshAllViews();
                statusBarLabel.setText("Imported " + imported + " courses from web.");
            }
        });
    }

    // ─── Async refresh ────────────────────────────────────────────────────

    /**
     * Full async refresh: redraws graph + catalog, then submits a background
     * compute task for stats, cycle detection, and the semester plan.
     */
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
            statusBarLabel.setText("Ready.");
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            busySpinner.setVisible(false);
            statusBarLabel.setText("Compute error: " + task.getException().getMessage());
        }));

        busySpinner.setVisible(true);
        AppExecutor.getInstance().computePool().submit(task);
    }

    /** Persists the entire graph to SQLite asynchronously on the I/O thread. */
    private void saveStateAsync() {
        DatabaseSaveTask saveTask = new DatabaseSaveTask(
                graph, DatabaseManager.getInstance().repository());
        saveTask.messageProperty().addListener((obs, o, msg) ->
                Platform.runLater(() -> statusBarLabel.setText(msg)));
        saveTask.setOnFailed(e ->
                Platform.runLater(() -> statusBarLabel.setText(
                        "Save failed: " + saveTask.getException().getMessage())));
        AppExecutor.getInstance().ioExecutor().submit(saveTask);
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
        bar.setStyle("-fx-background-color: #f8fafc; "
                + "-fx-border-color: #e2e8f0 transparent transparent transparent; "
                + "-fx-border-width: 1px;");
        return bar;
    }
}