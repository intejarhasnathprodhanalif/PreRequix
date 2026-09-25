package com.prerequix.ui;

import com.prerequix.model.CourseGraph;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * UI Component that detects and displays circular dependency conflicts.
 */
public class ConflictAlertPane extends VBox {

    private final Label titleLabel;
    private final Label pathLabel;

    public ConflictAlertPane() {
        setSpacing(6);
        setPadding(new Insets(12, 16, 12, 16));
        getStyleClass().add("conflict-banner");
        setVisible(false);
        setManaged(false);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 16px;");

        titleLabel = new Label("Circular Dependency Detected!");
        titleLabel.getStyleClass().add("conflict-text");
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        header.getChildren().addAll(icon, titleLabel);

        pathLabel = new Label();
        pathLabel.setWrapText(true);
        pathLabel.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 12px; -fx-font-family: monospace;");

        getChildren().addAll(header, pathLabel);
    }

    /**
     * Original method – runs cycle detection itself.
     * Still available for callers that don't use the background task.
     */
    public boolean updateConflictStatus(CourseGraph graph) {
        return applyComputedCycle(graph.detectCycle());
    }

    /**
     * Apply a pre-computed cycle path (from {@link com.prerequix.concurrent.GraphComputeTask}).
     * Must be called on the JavaFX Application Thread.
     *
     * @param cyclePath list of course IDs forming the cycle; empty means no cycle.
     * @return {@code true} if a cycle was visible.
     */
    public boolean applyComputedCycle(List<String> cyclePath) {
        if (!cyclePath.isEmpty()) {
            String cycleStr = String.join(" \u2794 ", cyclePath);
            pathLabel.setText("Loop path: " + cycleStr + "\nPlease edit prerequisite links to resolve this cycle.");
            setVisible(true);
            setManaged(true);
            return true;
        } else {
            setVisible(false);
            setManaged(false);
            return false;
        }
    }
}

