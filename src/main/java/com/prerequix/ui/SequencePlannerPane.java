package com.prerequix.ui;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.SemesterPlan;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Visual term-by-term roadmap sequence generator and semester planner.
 */
public class SequencePlannerPane extends BorderPane {

    private final Stage primaryStage;
    private final CourseGraph graph;
    private final Consumer<Course> onCourseSelectedListener;

    private final Slider creditSlider;
    private final Label creditValLabel;
    private final Label summaryStatsLabel;
    private final VBox termsContainer;

    public SequencePlannerPane(Stage primaryStage, CourseGraph graph, Consumer<Course> onCourseSelectedListener) {
        this.primaryStage = primaryStage;
        this.graph = graph;
        this.onCourseSelectedListener = onCourseSelectedListener;

        setPadding(new Insets(16));

        // Top Control Header
        VBox topHeader = new VBox(10);
        topHeader.getStyleClass().add("card-panel");
        topHeader.setPadding(new Insets(14, 18, 14, 18));

        Label title = new Label("🗓️ Valid Course-Taking Sequence Planner");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitle = new Label("Calculates topological course order and distributes remaining courses into terms based on credit limits.");
        subtitle.getStyleClass().add("muted-text");

        // Credit Slider Controls
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

        Button exportBtn = new Button("📥 Export Schedule Report");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setOnAction(e -> exportScheduleReport());

        HBox controlsRow = new HBox(16, sliderBox, summaryStatsLabel, new Region(), exportBtn);
        HBox.setHgrow(controlsRow.getChildren().get(2), Priority.ALWAYS);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        topHeader.getChildren().addAll(title, subtitle, new Separator(), controlsRow);
        setTop(topHeader);

        // Center Content Scroll Pane for Term Cards
        termsContainer = new VBox(16);
        termsContainer.setPadding(new Insets(16, 0, 16, 0));

        ScrollPane scrollPane = new ScrollPane(termsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        setCenter(scrollPane);

        generateRoadmap();
    }

    public void generateRoadmap() {
        termsContainer.getChildren().clear();

        try {
            double maxCredits = creditSlider.getValue();
            List<SemesterPlan> schedule = graph.generateSemesterPlan(maxCredits);

            if (schedule.isEmpty()) {
                Label empty = new Label("🎉 All courses in your curriculum are completed!");
                empty.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
                termsContainer.getChildren().add(empty);
                summaryStatsLabel.setText("0 Remaining Terms Required");
                return;
            }

            double totalRemainingCredits = schedule.stream()
                    .mapToDouble(SemesterPlan::getTotalCredits)
                    .sum();

            summaryStatsLabel.setText(String.format("🎓 %d Term%s Required (%.1f Total Credits)",
                    schedule.size(), schedule.size() > 1 ? "s" : "", totalRemainingCredits));

            FlowPane termsGrid = new FlowPane();
            termsGrid.setHgap(16);
            termsGrid.setVgap(16);

            for (SemesterPlan plan : schedule) {
                VBox termCard = createTermCard(plan, maxCredits);
                termsGrid.getChildren().add(termCard);
            }

            termsContainer.getChildren().add(termsGrid);

        } catch (IllegalStateException e) {
            Label errorLbl = new Label("⚠️ Cannot generate schedule: Graph contains circular prerequisite dependencies!");
            errorLbl.getStyleClass().add("conflict-text");
            termsContainer.getChildren().add(errorLbl);
            summaryStatsLabel.setText("Conflict Detected");
        }
    }

    private VBox createTermCard(SemesterPlan plan, double maxCredits) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-panel");
        card.setPrefWidth(280);

        Label termHeader = new Label("Semester / Term " + plan.getTermNumber());
        termHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        Label creditBadge = new Label(String.format("%.1f / %.1f Credits", plan.getTotalCredits(), maxCredits));
        creditBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #64748b;");

        HBox top = new HBox(10, termHeader, new Region(), creditBadge);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);

        ProgressBar progress = new ProgressBar(plan.getTotalCredits() / maxCredits);
        progress.setPrefWidth(260);
        progress.setStyle("-fx-accent: #3b82f6;");

        VBox coursesList = new VBox(6);
        for (Course c : plan.getCourses()) {
            HBox courseItem = new HBox(8);
            courseItem.setAlignment(Pos.CENTER_LEFT);
            courseItem.setPadding(new Insets(6, 8, 6, 8));
            courseItem.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 6px; -fx-cursor: hand;");

            Label code = new Label(c.getCode());
            code.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            Label title = new Label(c.getTitle());
            title.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

            Label credits = new Label(c.getCredits() + " cr");
            credits.setStyle("-fx-font-size: 11px; -fx-font-weight: 600;");

            courseItem.getChildren().addAll(code, title, new Region(), credits);
            HBox.setHgrow(courseItem.getChildren().get(2), Priority.ALWAYS);

            courseItem.setOnMouseClicked(e -> {
                if (onCourseSelectedListener != null) {
                    onCourseSelectedListener.accept(c);
                }
            });

            coursesList.getChildren().add(courseItem);
        }

        card.getChildren().addAll(top, progress, new Separator(), coursesList);
        return card;
    }

    private void exportScheduleReport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Course Sequence Schedule");
        fileChooser.setInitialFileName("Course_Sequence_Schedule.md");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Document", "*.md"));

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try (PrintWriter writer = new PrintWriter(file)) {
            double maxCredits = creditSlider.getValue();
            List<SemesterPlan> schedule = graph.generateSemesterPlan(maxCredits);

            writer.println("# Course Prerequisite Sequence Roadmap");
            writer.println("Generated by PreRequix Planner");
            writer.println("Max Credit Hours Per Term: " + maxCredits);
            writer.println();

            for (SemesterPlan plan : schedule) {
                writer.println("## Term " + plan.getTermNumber() + " (" + plan.getTotalCredits() + " Credits)");
                for (Course c : plan.getCourses()) {
                    writer.println(String.format("- **%s**: %s (%.1f credits, %s)",
                            c.getCode(), c.getTitle(), c.getCredits(), c.getDepartment()));
                }
                writer.println();
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(primaryStage);
            alert.setTitle("Report Exported");
            alert.setHeaderText("Schedule Report Saved Successfully!");
            alert.setContentText("File saved to: " + file.getAbsolutePath());
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(primaryStage);
            alert.setTitle("Export Error");
            alert.setHeaderText("Failed to export schedule report");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
}

