package com.prerequix.ui;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.CourseStatus;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;

import java.util.*;
import java.util.function.Consumer;

/**
 * Interactive visual directed graph component displaying courses as nodes and prerequisites as directed edges.
 */
public class GraphViewPane extends BorderPane {

    private final CourseGraph graph;
    private final Consumer<Course> onCourseSelectedListener;

    private final Pane graphCanvas;
    private final Group graphGroup;

    private final Map<String, NodeCard> nodeCards = new HashMap<>();
    private final List<EdgeConnector> edgeConnectors = new ArrayList<>();

    private Course selectedCourse = null;

    private final DoubleProperty scaleFactor = new SimpleDoubleProperty(1.0);
    private double mouseAnchorX;
    private double mouseAnchorY;
    private double translateAnchorX;
    private double translateAnchorY;

    public GraphViewPane(CourseGraph graph, Consumer<Course> onCourseSelectedListener) {
        this.graph = graph;
        this.onCourseSelectedListener = onCourseSelectedListener;

        graphGroup = new Group();
        graphCanvas = new Pane(graphGroup);
        graphCanvas.setPrefSize(2000, 1500);
        graphCanvas.setStyle("-fx-background-color: #f8fafc;");

        ScrollPane scrollPane = new ScrollPane(graphCanvas);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // Pan & Zoom controls toolbar
        HBox toolbar = createControlsToolbar();
        setTop(toolbar);
        setCenter(scrollPane);

        // Zoom transforms
        graphGroup.scaleXProperty().bind(scaleFactor);
        graphGroup.scaleYProperty().bind(scaleFactor);

        setupPanAndZoom(graphCanvas);

        // Initial render
        renderGraph();
    }

    private HBox createControlsToolbar() {
        Label title = new Label("🕸️ Prerequisite Network Graph");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #0f172a;");

        Label subtitle = new Label("Click node to highlight prerequisite path • Drag nodes to reposition");
        subtitle.getStyleClass().add("muted-text");

        VBox titleBox = new VBox(2, title, subtitle);

        Button zoomInBtn = new Button("➕");
        zoomInBtn.getStyleClass().add("btn-secondary");
        zoomInBtn.setOnAction(e -> scaleFactor.set(Math.min(2.0, scaleFactor.get() + 0.15)));

        Button zoomOutBtn = new Button("➖");
        zoomOutBtn.getStyleClass().add("btn-secondary");
        zoomOutBtn.setOnAction(e -> scaleFactor.set(Math.max(0.4, scaleFactor.get() - 0.15)));

        Button resetBtn = new Button("🎯 Reset View");
        resetBtn.getStyleClass().add("btn-secondary");
        resetBtn.setOnAction(e -> {
            scaleFactor.set(1.0);
            graphGroup.setTranslateX(0);
            graphGroup.setTranslateY(0);
            renderGraph();
        });

        HBox controls = new HBox(8, zoomInBtn, zoomOutBtn, resetBtn);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox topBar = new HBox(12, titleBox, new Region(), controls);
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 16, 10, 16));
        topBar.getStyleClass().add("card-panel");
        return topBar;
    }

    private void setupPanAndZoom(Pane canvas) {
        canvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown() && e.getTarget() == canvas) {
                mouseAnchorX = e.getSceneX();
                mouseAnchorY = e.getSceneY();
                translateAnchorX = graphGroup.getTranslateX();
                translateAnchorY = graphGroup.getTranslateY();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown() && e.getTarget() == canvas) {
                graphGroup.setTranslateX(translateAnchorX + (e.getSceneX() - mouseAnchorX));
                graphGroup.setTranslateY(translateAnchorY + (e.getSceneY() - mouseAnchorY));
            }
        });

        canvas.setOnScroll(e -> {
            if (e.getDeltaY() != 0) {
                double zoomFactor = e.getDeltaY() > 0 ? 1.08 : 0.92;
                scaleFactor.set(Math.max(0.4, Math.min(2.0, scaleFactor.get() * zoomFactor)));
            }
        });
    }

    public void renderGraph() {
        graphGroup.getChildren().clear();
        nodeCards.clear();
        edgeConnectors.clear();

        Collection<Course> courses = graph.getAllCourses();
        if (courses.isEmpty()) {
            Label emptyLbl = new Label("No courses added yet. Click '+ Add Course' to start!");
            emptyLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
            emptyLbl.setLayoutX(100);
            emptyLbl.setLayoutY(100);
            graphGroup.getChildren().add(emptyLbl);
            return;
        }

        // Assign hierarchical depth columns
        Map<Integer, List<Course>> depthLayers = new TreeMap<>();
        for (Course c : courses) {
            int depth = graph.getPrerequisiteDepth(c.getId());
            depthLayers.computeIfAbsent(depth, k -> new ArrayList<>()).add(c);
        }

        double startX = 60;
        double colWidth = 260;
        double rowHeight = 110;
        double startY = 60;

        // Position nodes in grid columns per tier
        for (Map.Entry<Integer, List<Course>> entry : depthLayers.entrySet()) {
            int col = entry.getKey();
            List<Course> list = entry.getValue();
            double currentX = startX + col * colWidth;

            for (int row = 0; row < list.size(); row++) {
                Course c = list.get(row);
                double currentY = startY + row * rowHeight;

                NodeCard card = new NodeCard(c);
                card.setLayoutX(currentX);
                card.setLayoutY(currentY);

                makeDraggable(card);

                nodeCards.put(c.getId(), card);
                graphGroup.getChildren().add(card);
            }
        }

        // Draw directed Bezier edges (prereq -> course)
        for (Course c : courses) {
            NodeCard targetNode = nodeCards.get(c.getId());
            if (targetNode == null) continue;

            for (String prereqId : c.getPrerequisiteIds()) {
                NodeCard prereqNode = nodeCards.get(prereqId);
                if (prereqNode != null) {
                    EdgeConnector edge = new EdgeConnector(prereqNode, targetNode, prereqId, c.getId());
                    edgeConnectors.add(edge);
                    graphGroup.getChildren().add(0, edge.getGroup()); // Add edges behind nodes
                }
            }
        }

        if (selectedCourse != null) {
            highlightPath(selectedCourse);
        }
    }

    private void makeDraggable(NodeCard card) {
        final Point2D[] dragDelta = new Point2D[1];

        card.setOnMousePressed(e -> {
            dragDelta[0] = new Point2D(card.getLayoutX() - e.getSceneX(), card.getLayoutY() - e.getSceneY());
            selectCourse(card.getCourse());
            e.consume();
        });

        card.setOnMouseDragged(e -> {
            if (dragDelta[0] != null) {
                card.setLayoutX(e.getSceneX() + dragDelta[0].getX());
                card.setLayoutY(e.getSceneY() + dragDelta[0].getY());
                updateEdgePositions();
                e.consume();
            }
        });
    }

    private void updateEdgePositions() {
        for (EdgeConnector edge : edgeConnectors) {
            edge.update();
        }
    }

    public void selectCourse(Course course) {
        this.selectedCourse = course;
        highlightPath(course);
        if (onCourseSelectedListener != null && course != null) {
            onCourseSelectedListener.accept(course);
        }
    }

    private void highlightPath(Course target) {
        if (target == null) {
            for (NodeCard card : nodeCards.values()) {
                card.setHighlightState(HighlightState.NORMAL);
            }
            for (EdgeConnector edge : edgeConnectors) {
                edge.setHighlighted(false, false);
            }
            return;
        }

        Set<Course> upstreamPrereqs = graph.getAllPrerequisites(target.getId());
        Set<Course> downstreamDependents = graph.getAllDependents(target.getId());

        Set<String> upstreamIds = new HashSet<>();
        for (Course c : upstreamPrereqs) upstreamIds.add(c.getId());

        Set<String> downstreamIds = new HashSet<>();
        for (Course c : downstreamDependents) downstreamIds.add(c.getId());

        for (NodeCard card : nodeCards.values()) {
            String id = card.getCourse().getId();
            if (id.equals(target.getId())) {
                card.setHighlightState(HighlightState.TARGET);
            } else if (upstreamIds.contains(id)) {
                card.setHighlightState(HighlightState.PREREQUISITE);
            } else if (downstreamIds.contains(id)) {
                card.setHighlightState(HighlightState.DEPENDENT);
            } else {
                card.setHighlightState(HighlightState.DIMMED);
            }
        }

        for (EdgeConnector edge : edgeConnectors) {
            boolean isUpstream = (upstreamIds.contains(edge.sourceId) || edge.sourceId.equals(target.getId()))
                    && (upstreamIds.contains(edge.targetId) || edge.targetId.equals(target.getId()));

            boolean isDownstream = (downstreamIds.contains(edge.sourceId) || edge.sourceId.equals(target.getId()))
                    && (downstreamIds.contains(edge.targetId) || edge.targetId.equals(target.getId()));

            edge.setHighlighted(isUpstream, isDownstream);
        }
    }

    enum HighlightState { NORMAL, TARGET, PREREQUISITE, DEPENDENT, DIMMED }

    // --- Inner Class: Visual Node Card ---
    private class NodeCard extends VBox {
        private final Course course;
        private final Label statusDot;

        public NodeCard(Course course) {
            this.course = course;
            setSpacing(4);
            setPadding(new Insets(8, 12, 8, 12));
            setPrefWidth(180);

            getStyleClass().add("card-panel");
            setStyle("-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;");

            statusDot = new Label(getStatusIcon(course.getStatus()));
            statusDot.setStyle("-fx-font-size: 12px;");

            Label codeLbl = new Label(course.getCode());
            codeLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

            Label creditsLbl = new Label(course.getCredits() + " cr");
            creditsLbl.getStyleClass().add("muted-text");

            HBox header = new HBox(6, statusDot, codeLbl, new Region(), creditsLbl);
            HBox.setHgrow(header.getChildren().get(2), Priority.ALWAYS);
            header.setAlignment(Pos.CENTER_LEFT);

            Label titleLbl = new Label(course.getTitle());
            titleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            titleLbl.setWrapText(false);

            getChildren().addAll(header, titleLbl);

            Tooltip.install(this, new Tooltip(course.getCode() + ": " + course.getTitle() + "\n" + course.getDescription()));
        }

        private String getStatusIcon(CourseStatus status) {
            switch (status) {
                case COMPLETED: return "🟢";
                case IN_PROGRESS: return "🟡";
                default: return "⚪";
            }
        }

        public Course getCourse() {
            return course;
        }

        public void setHighlightState(HighlightState state) {
            setOpacity(1.0);
            switch (state) {
                case TARGET:
                    setStyle("-fx-border-color: #3b82f6; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(59, 130, 246, 0.4), 10, 0, 0, 2);");
                    break;
                case PREREQUISITE:
                    setStyle("-fx-border-color: #f59e0b; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
                    break;
                case DEPENDENT:
                    setStyle("-fx-border-color: #10b981; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
                    break;
                case DIMMED:
                    setOpacity(0.35);
                    setStyle("-fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
                    break;
                case NORMAL:
                default:
                    setStyle("-fx-border-color: #e2e8f0; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
                    break;
            }
        }
    }

    // --- Inner Class: Directed Edge Connector ---
    private class EdgeConnector {
        private final NodeCard sourceCard;
        private final NodeCard targetCard;
        private final String sourceId;
        private final String targetId;

        private final Group group;
        private final CubicCurve curve;
        private final Polygon arrowHead;

        public EdgeConnector(NodeCard sourceCard, NodeCard targetCard, String sourceId, String targetId) {
            this.sourceCard = sourceCard;
            this.targetCard = targetCard;
            this.sourceId = sourceId;
            this.targetId = targetId;

            curve = new CubicCurve();
            curve.setFill(null);
            curve.setStroke(Color.web("#94a3b8"));
            curve.setStrokeWidth(1.8);

            arrowHead = new Polygon(0.0, 0.0, -8.0, 4.0, -8.0, -4.0);
            arrowHead.setFill(Color.web("#94a3b8"));

            group = new Group(curve, arrowHead);
            update();
        }

        public Group getGroup() {
            return group;
        }

        public void update() {
            double startX = sourceCard.getLayoutX() + sourceCard.getPrefWidth();
            double startY = sourceCard.getLayoutY() + 25;

            double endX = targetCard.getLayoutX();
            double endY = targetCard.getLayoutY() + 25;

            double controlOffsetX = Math.abs(endX - startX) * 0.5;

            curve.setStartX(startX);
            curve.setStartY(startY);
            curve.setControlX1(startX + controlOffsetX);
            curve.setControlY1(startY);
            curve.setControlX2(endX - controlOffsetX);
            curve.setControlY2(endY);
            curve.setEndX(endX);
            curve.setEndY(endY);

            arrowHead.setLayoutX(endX);
            arrowHead.setLayoutY(endY);

            double angle = Math.atan2(endY - (curve.getControlY2()), endX - (curve.getControlX2()));
            arrowHead.setRotate(Math.toDegrees(angle));
        }

        public void setHighlighted(boolean isUpstream, boolean isDownstream) {
            if (isUpstream) {
                curve.setStroke(Color.web("#f59e0b"));
                curve.setStrokeWidth(2.8);
                arrowHead.setFill(Color.web("#f59e0b"));
                group.setOpacity(1.0);
            } else if (isDownstream) {
                curve.setStroke(Color.web("#10b981"));
                curve.setStrokeWidth(2.8);
                arrowHead.setFill(Color.web("#10b981"));
                group.setOpacity(1.0);
            } else {
                curve.setStroke(Color.web("#94a3b8"));
                curve.setStrokeWidth(1.5);
                arrowHead.setFill(Color.web("#94a3b8"));
                group.setOpacity(0.25);
            }
        }
    }
}

