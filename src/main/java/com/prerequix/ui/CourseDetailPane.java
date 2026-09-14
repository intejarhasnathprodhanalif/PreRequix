package com.prerequix.ui;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.CourseStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Inspector panel displaying detailed prerequisite relationships and status controls for a selected course.
 */
public class CourseDetailPane extends VBox {

    private final CourseGraph graph;
    private final Consumer<Course> onStatusChangedListener;
    private final Consumer<Course> onCourseSelectedListener;
    private final Consumer<Course> onEditCourseListener;

    private Course currentCourse;

    private final Label titleLabel;
    private final Label codeLabel;
    private final Label deptLabel;
    private final Label creditsLabel;
    private final Label descLabel;
    private final Label statusPill;
    private final Button markCompletedBtn;
    private final Button markInProgressBtn;
    private final Button markUncompletedBtn;
    private final Button editCourseBtn;

    private final VBox directPrereqsBox;
    private final VBox indirectPrereqsBox;
    private final VBox dependentsBox;

    public CourseDetailPane(CourseGraph graph,
                            Consumer<Course> onStatusChangedListener,
                            Consumer<Course> onCourseSelectedListener,
                            Consumer<Course> onEditCourseListener) {
        this.graph = graph;
        this.onStatusChangedListener = onStatusChangedListener;
        this.onCourseSelectedListener = onCourseSelectedListener;
        this.onEditCourseListener = onEditCourseListener;

        setSpacing(14);
        setPadding(new Insets(18));
        getStyleClass().add("card-panel");
        setPrefWidth(340);

        // Title Header
        codeLabel = new Label("Select a Course");
        codeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        titleLabel = new Label("Click any course card or node to view its prerequisite path.");
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("muted-text");

        statusPill = new Label("UNCOMPLETED");
        statusPill.getStyleClass().addAll("status-pill", "status-uncompleted");

        HBox topRow = new HBox(10, codeLabel, statusPill);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Metadata grid
        deptLabel = new Label("-");
        deptLabel.getStyleClass().add("muted-text");

        creditsLabel = new Label("- cr");
        creditsLabel.getStyleClass().add("muted-text");

        HBox metaRow = new HBox(12, deptLabel, new Label("•"), creditsLabel);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        descLabel = new Label("");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px;");

        // Action Buttons
        markCompletedBtn = new Button("✓ Mark Completed");
        markCompletedBtn.getStyleClass().add("btn-primary");
        markCompletedBtn.setOnAction(e -> changeStatus(CourseStatus.COMPLETED));

        markInProgressBtn = new Button("⏳ In Progress");
        markInProgressBtn.getStyleClass().add("btn-secondary");
        markInProgressBtn.setOnAction(e -> changeStatus(CourseStatus.IN_PROGRESS));

        markUncompletedBtn = new Button("↺ Reset");
        markUncompletedBtn.getStyleClass().add("btn-secondary");
        markUncompletedBtn.setOnAction(e -> changeStatus(CourseStatus.UNCOMPLETED));

        editCourseBtn = new Button("✏️ Edit");
        editCourseBtn.getStyleClass().add("btn-secondary");
        editCourseBtn.setOnAction(e -> {
            if (currentCourse != null && onEditCourseListener != null) {
                onEditCourseListener.accept(currentCourse);
            }
        });

        HBox actionsRow = new HBox(8, markCompletedBtn, markInProgressBtn, markUncompletedBtn, editCourseBtn);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        // Prerequisite lists containers
        directPrereqsBox = new VBox(6);
        indirectPrereqsBox = new VBox(6);
        dependentsBox = new VBox(6);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox contentBox = new VBox(14,
                topRow,
                titleLabel,
                metaRow,
                descLabel,
                actionsRow,
                new Separator(),
                createSection("Direct Prerequisites (Immediate Requirements)", directPrereqsBox),
                createSection("Indirect Prerequisites (Transitive Closure)", indirectPrereqsBox),
                createSection("Unlocked Downstream Courses (Dependents)", dependentsBox)
        );

        scroll.setContent(contentBox);
        getChildren().add(scroll);
        setVgrow(scroll, Priority.ALWAYS);

        clearDetails();
    }

    private VBox createSection(String title, VBox box) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-weight: 600; -fx-font-size: 12px; -fx-text-fill: var(--text-main);");
        VBox sec = new VBox(6, lbl, box);
        return sec;
    }

    public void displayCourse(Course course) {
        this.currentCourse = course;
        if (course == null) {
            clearDetails();
            return;
        }

        codeLabel.setText(course.getCode());
        titleLabel.setText(course.getTitle());
        deptLabel.setText(course.getDepartment());
        creditsLabel.setText(course.getCredits() + " Credits");
        descLabel.setText(course.getDescription() != null ? course.getDescription() : "No description provided.");

        statusPill.setText(course.getStatus().getDisplayName());
        statusPill.getStyleClass().removeAll("status-completed", "status-in-progress", "status-uncompleted");
        statusPill.getStyleClass().add(course.getStatus().getCssClass());

        markCompletedBtn.setDisable(false);
        markInProgressBtn.setDisable(false);
        markUncompletedBtn.setDisable(false);
        editCourseBtn.setDisable(false);

        // Render Direct Prerequisites
        directPrereqsBox.getChildren().clear();
        Set<Course> direct = graph.getDirectPrerequisites(course.getId());
        if (direct.isEmpty()) {
            Label emptyLbl = new Label("None (Entry-level course)");
            emptyLbl.getStyleClass().add("muted-text");
            directPrereqsBox.getChildren().add(emptyLbl);
        } else {
            for (Course c : direct) {
                directPrereqsBox.getChildren().add(createCourseListItem(c));
            }
        }

        // Render Indirect Prerequisites
        indirectPrereqsBox.getChildren().clear();
        Set<Course> indirect = graph.getIndirectPrerequisites(course.getId());
        if (indirect.isEmpty()) {
            Label emptyLbl = new Label("None");
            emptyLbl.getStyleClass().add("muted-text");
            indirectPrereqsBox.getChildren().add(emptyLbl);
        } else {
            for (Course c : indirect) {
                indirectPrereqsBox.getChildren().add(createCourseListItem(c));
            }
        }

        // Render Dependents
        dependentsBox.getChildren().clear();
        Set<Course> dependents = graph.getDirectDependents(course.getId());
        if (dependents.isEmpty()) {
            Label emptyLbl = new Label("None (Terminal course)");
            emptyLbl.getStyleClass().add("muted-text");
            dependentsBox.getChildren().add(emptyLbl);
        } else {
            for (Course c : dependents) {
                dependentsBox.getChildren().add(createCourseListItem(c));
            }
        }
    }

    private HBox createCourseListItem(Course course) {
        Label code = new Label(course.getCode());
        code.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");

        Label name = new Label(course.getTitle());
        name.getStyleClass().add("muted-text");

        Label pill = new Label(course.getStatus().getDisplayName());
        pill.getStyleClass().addAll("status-pill", course.getStatus().getCssClass());
        pill.setStyle("-fx-font-size: 10px;");

        HBox item = new HBox(8, code, name, new Region(), pill);
        HBox.setHgrow(item.getChildren().get(2), Priority.ALWAYS);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(4, 8, 4, 8));
        item.setStyle("-fx-background-color: var(--border-light); -fx-background-radius: 6px; -fx-cursor: hand;");

        item.setOnMouseClicked(e -> {
            if (onCourseSelectedListener != null) {
                onCourseSelectedListener.accept(course);
            }
        });

        return item;
    }

    private void changeStatus(CourseStatus newStatus) {
        if (currentCourse != null) {
            currentCourse.setStatus(newStatus);
            displayCourse(currentCourse);
            if (onStatusChangedListener != null) {
                onStatusChangedListener.accept(currentCourse);
            }
        }
    }

    public void clearDetails() {
        currentCourse = null;
        codeLabel.setText("Select a Course");
        titleLabel.setText("Click any course card or graph node to inspect its complete prerequisite path.");
        deptLabel.setText("-");
        creditsLabel.setText("- cr");
        descLabel.setText("");
        statusPill.setText("UNCOMPLETED");
        statusPill.getStyleClass().removeAll("status-completed", "status-in-progress", "status-uncompleted");
        statusPill.getStyleClass().add("status-uncompleted");

        markCompletedBtn.setDisable(true);
        markInProgressBtn.setDisable(true);
        markUncompletedBtn.setDisable(true);
        editCourseBtn.setDisable(true);

        directPrereqsBox.getChildren().clear();
        indirectPrereqsBox.getChildren().clear();
        dependentsBox.getChildren().clear();
    }
}
