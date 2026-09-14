package com.prerequix.ui;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.CourseStatus;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Filterable table and catalog view for searching, filtering, adding, editing, and deleting courses.
 */
public class CourseCatalogPane extends BorderPane {

    private final Stage primaryStage;
    private final CourseGraph graph;
    private final Consumer<Course> onCourseSelectedListener;
    private final Runnable onGraphUpdatedListener;

    private final TableView<Course> tableView;
    private final ObservableList<Course> tableData = FXCollections.observableArrayList();

    private final TextField searchField;
    private String currentFilter = "ALL";

    public CourseCatalogPane(Stage primaryStage,
                             CourseGraph graph,
                             Consumer<Course> onCourseSelectedListener,
                             Runnable onGraphUpdatedListener) {
        this.primaryStage = primaryStage;
        this.graph = graph;
        this.onCourseSelectedListener = onCourseSelectedListener;
        this.onGraphUpdatedListener = onGraphUpdatedListener;

        setPadding(new Insets(16));

        // Header Control Bar
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 14, 0));

        searchField = new TextField();
        searchField.setPromptText("🔍 Search course code, title, or department...");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshTable());

        // Status Filter Toggle Buttons
        ToggleGroup filterGroup = new ToggleGroup();

        RadioButton allBtn = createFilterRadioButton("All", "ALL", filterGroup, true);
        RadioButton availableBtn = createFilterRadioButton("Available Now ⚡", "AVAILABLE", filterGroup, false);
        RadioButton completedBtn = createFilterRadioButton("Completed ✓", "COMPLETED", filterGroup, false);
        RadioButton inProgressBtn = createFilterRadioButton("In Progress ⏳", "IN_PROGRESS", filterGroup, false);
        RadioButton uncompletedBtn = createFilterRadioButton("Locked 🔒", "UNCOMPLETED", filterGroup, false);

        HBox filterBox = new HBox(8, allBtn, availableBtn, completedBtn, inProgressBtn, uncompletedBtn);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        Button addCourseBtn = new Button("➕ Add Course");
        addCourseBtn.getStyleClass().add("btn-primary");
        addCourseBtn.setOnAction(e -> openAddCourseDialog(null));

        topBar.getChildren().addAll(searchField, filterBox, new Region(), addCourseBtn);
        HBox.setHgrow(topBar.getChildren().get(2), Priority.ALWAYS);

        setTop(topBar);

        // Table Setup
        tableView = new TableView<>();
        tableView.getStyleClass().add("table-view");
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Course, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        codeCol.setPrefWidth(90);

        TableColumn<Course, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Course, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(new PropertyValueFactory<>("department"));
        deptCol.setPrefWidth(140);

        TableColumn<Course, Double> creditsCol = new TableColumn<>("Credits");
        creditsCol.setCellValueFactory(new PropertyValueFactory<>("credits"));
        creditsCol.setPrefWidth(70);

        TableColumn<Course, String> prereqCol = new TableColumn<>("Direct Prerequisites");
        prereqCol.setCellValueFactory(cellData -> {
            Set<Course> prereqs = graph.getDirectPrerequisites(cellData.getValue().getId());
            String text = prereqs.stream().map(Course::getCode).collect(Collectors.joining(", "));
            return new SimpleStringProperty(text.isEmpty() ? "None" : text);
        });
        prereqCol.setPrefWidth(180);

        TableColumn<Course, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().getDisplayName()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Course course = getTableView().getItems().get(getIndex());
                    Label pill = new Label(course.getStatus().getDisplayName());
                    pill.getStyleClass().addAll("status-pill", course.getStatus().getCssClass());
                    setGraphic(pill);
                    setText(null);
                }
            }
        });
        statusCol.setPrefWidth(130);

        TableColumn<Course, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏️");
            private final Button deleteBtn = new Button("🗑️");
            private final HBox btnBox = new HBox(6, editBtn, deleteBtn);

            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setOnAction(e -> {
                    Course course = getTableView().getItems().get(getIndex());
                    openAddCourseDialog(course);
                });

                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setOnAction(e -> {
                    Course course = getTableView().getItems().get(getIndex());
                    deleteCourse(course);
                });

                btnBox.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnBox);
            }
        });
        actionsCol.setPrefWidth(100);

        tableView.getColumns().addAll(codeCol, titleCol, deptCol, creditsCol, prereqCol, statusCol, actionsCol);
        tableView.setItems(tableData);

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onCourseSelectedListener != null) {
                onCourseSelectedListener.accept(newVal);
            }
        });

        setCenter(tableView);

        refreshTable();
    }

    private RadioButton createFilterRadioButton(String label, String filterKey, ToggleGroup group, boolean selected) {
        RadioButton rb = new RadioButton(label);
        rb.setToggleGroup(group);
        rb.setSelected(selected);
        rb.setOnAction(e -> {
            currentFilter = filterKey;
            refreshTable();
        });
        return rb;
    }

    public void refreshTable() {
        String searchText = searchField.getText().trim().toLowerCase();
        List<Course> filtered = graph.getAllCourses().stream().filter(c -> {
            // Text search
            boolean matchesSearch = searchText.isEmpty()
                    || c.getCode().toLowerCase().contains(searchText)
                    || c.getTitle().toLowerCase().contains(searchText)
                    || c.getDepartment().toLowerCase().contains(searchText);

            if (!matchesSearch) return false;

            // Category filter
            switch (currentFilter) {
                case "AVAILABLE":
                    return graph.isCourseAvailable(c.getId());
                case "COMPLETED":
                    return c.isCompleted();
                case "IN_PROGRESS":
                    return c.isInProgress();
                case "UNCOMPLETED":
                    return !c.isCompleted() && !graph.isCourseAvailable(c.getId());
                case "ALL":
                default:
                    return true;
            }
        }).collect(Collectors.toList());

        tableData.setAll(filtered);
    }

    private void openAddCourseDialog(Course existingCourse) {
        CourseDialog dialog = new CourseDialog(primaryStage, graph, existingCourse);
        dialog.showAndWait();
        if (dialog.isSaved()) {
            refreshTable();
            if (onGraphUpdatedListener != null) {
                onGraphUpdatedListener.run();
            }
        }
    }

    private void deleteCourse(Course course) {
        if (course == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(primaryStage);
        alert.setTitle("Delete Course");
        alert.setHeaderText("Delete " + course.getCode() + "?");
        alert.setContentText("This will remove the course and its prerequisite connections from the graph.");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                graph.removeCourse(course.getId());
                refreshTable();
                if (onGraphUpdatedListener != null) {
                    onGraphUpdatedListener.run();
                }
            }
        });
    }
}
