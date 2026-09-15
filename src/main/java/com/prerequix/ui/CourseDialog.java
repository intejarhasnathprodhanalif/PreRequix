package com.prerequix.ui;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.CourseStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Modal dialog for creating and editing courses and prerequisites.
 */
public class CourseDialog extends Stage {

    private final CourseGraph graph;
    private final Course targetCourse;
    private boolean saved = false;

    private final TextField codeField;
    private final TextField titleField;
    private final Spinner<Double> creditsSpinner;
    private final TextField deptField;
    private final TextArea descArea;
    private final ComboBox<CourseStatus> statusCombo;
    private final ListView<CheckBox> prereqListView;
    private final Label warningLabel;

    public CourseDialog(Stage owner, CourseGraph graph, Course existingCourse) {
        this.graph = graph;
        this.targetCourse = existingCourse;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(existingCourse == null ? "Add New Course" : "Edit Course: " + existingCourse.getCode());

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("card-panel");

        Label header = new Label(existingCourse == null ? "✨ Create New Course" : "✏️ Edit Course Details");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        codeField = new TextField(existingCourse != null ? existingCourse.getCode() : "");
        codeField.setPromptText("e.g. CS 101");
        grid.add(new Label("Course Code:"), 0, 0);
        grid.add(codeField, 1, 0);

        titleField = new TextField(existingCourse != null ? existingCourse.getTitle() : "");
        titleField.setPromptText("e.g. Intro to Computer Science");
        grid.add(new Label("Course Title:"), 0, 1);
        grid.add(titleField, 1, 1);

        double initialCredits = existingCourse != null ? existingCourse.getCredits() : 3.0;
        creditsSpinner = new Spinner<>(0.5, 12.0, initialCredits, 0.5);
        creditsSpinner.setEditable(true);
        grid.add(new Label("Credits:"), 0, 2);
        grid.add(creditsSpinner, 1, 2);

        deptField = new TextField(existingCourse != null ? existingCourse.getDepartment() : "Computer Science");
        deptField.setPromptText("e.g. Computer Science");
        grid.add(new Label("Department:"), 0, 3);
        grid.add(deptField, 1, 3);

        statusCombo = new ComboBox<>();
        statusCombo.getItems().setAll(CourseStatus.values());
        statusCombo.setValue(existingCourse != null ? existingCourse.getStatus() : CourseStatus.UNCOMPLETED);
        grid.add(new Label("Status:"), 0, 4);
        grid.add(statusCombo, 1, 4);

        descArea = new TextArea(existingCourse != null ? existingCourse.getDescription() : "");
        descArea.setPromptText("Brief description of syllabus and course outcomes...");
        descArea.setPrefRowCount(3);
        grid.add(new Label("Description:"), 0, 5);
        grid.add(descArea, 1, 5);

        // Prerequisite Multi-select Checklist
        Label prereqLabel = new Label("Select Prerequisites:");
        prereqLabel.setStyle("-fx-font-weight: 600;");

        prereqListView = new ListView<>();
        prereqListView.setPrefHeight(140);

        Set<String> currentPrereqs = existingCourse != null ? existingCourse.getPrerequisiteIds() : Collections.emptySet();
        String selfId = existingCourse != null ? existingCourse.getId() : "";

        List<Course> otherCourses = graph.getAllCourses().stream()
                .filter(c -> !c.getId().equalsIgnoreCase(selfId))
                .sorted(Comparator.comparing(Course::getCode))
                .collect(Collectors.toList());

        for (Course c : otherCourses) {
            CheckBox cb = new CheckBox(c.getCode() + " - " + c.getTitle());
            cb.setUserData(c.getId());
            if (currentPrereqs.contains(c.getId())) {
                cb.setSelected(true);
            }
            cb.setOnAction(e -> validateCycleWarning());
            prereqListView.getItems().add(cb);
        }

        warningLabel = new Label();
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 12px;");

        // Action Buttons
        Button saveBtn = new Button(existingCourse == null ? "Create Course" : "Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> onSave());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> close());

        HBox btnRow = new HBox(10, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(header, grid, prereqLabel, prereqListView, warningLabel, btnRow);

        Scene scene = new Scene(root, 480, 580);
        if (owner.getScene() != null && owner.getScene().getStylesheets() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        setScene(scene);
    }

    private Set<String> getSelectedPrerequisiteIds() {
        Set<String> ids = new HashSet<>();
        for (CheckBox cb : prereqListView.getItems()) {
            if (cb.isSelected()) {
                ids.add((String) cb.getUserData());
            }
        }
        return ids;
    }

    private void validateCycleWarning() {
        warningLabel.setText("");
        String targetId = targetCourse != null ? targetCourse.getId() : Course.sanitizeId(codeField.getText());
        if (targetId.isBlank()) return;

        Set<String> selectedPrereqs = getSelectedPrerequisiteIds();
        for (String prereqId : selectedPrereqs) {
            if (graph.hasCourse(targetId) && graph.wouldCauseCycle(targetId, prereqId)) {
                warningLabel.setText("⚠️ Warning: Selecting " + prereqId + " as prerequisite will create a circular dependency!");
                return;
            }
        }
    }

    private void onSave() {
        String code = codeField.getText().trim();
        String title = titleField.getText().trim();

        if (code.isEmpty() || title.isEmpty()) {
            warningLabel.setText("Please fill in both Course Code and Title.");
            return;
        }

        String id = targetCourse != null ? targetCourse.getId() : Course.sanitizeId(code);
        double credits = creditsSpinner.getValue();
        String dept = deptField.getText().trim();
        String desc = descArea.getText().trim();
        CourseStatus status = statusCombo.getValue();
        Set<String> prereqs = getSelectedPrerequisiteIds();

        Course course = new Course(id, code, title, desc, credits, dept, status);
        course.setPrerequisiteIds(prereqs);

        if (targetCourse == null) {
            graph.addCourse(course);
        } else {
            targetCourse.setCode(code);
            targetCourse.setTitle(title);
            targetCourse.setCredits(credits);
            targetCourse.setDepartment(dept);
            targetCourse.setDescription(desc);
            targetCourse.setStatus(status);
            targetCourse.setPrerequisiteIds(prereqs);
            graph.addCourse(targetCourse);
        }

        saved = true;
        close();
    }

    public boolean isSaved() {
        return saved;
    }
}

