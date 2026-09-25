package com.prerequix.concurrent;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import com.prerequix.model.SemesterPlan;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Background {@link Task} that performs all expensive graph computations
 * on a non-UI thread and returns a {@link GraphComputeResult} snapshot.
 *
 * Computation includes:
 * <ul>
 *   <li>Collecting stats (total, completed, available, credits)</li>
 *   <li>Running DFS cycle detection</li>
 *   <li>Generating the topological semester sequence plan</li>
 * </ul>
 *
 * Usage:
 * <pre>{@code
 *   GraphComputeTask task = new GraphComputeTask(graph, maxCredits);
 *   task.setOnSucceeded(e -> applyResult(task.getValue()));
 *   task.setOnFailed(e -> handleError(task.getException()));
 *   AppExecutor.getInstance().computePool().submit(task);
 * }</pre>
 */
public class GraphComputeTask extends Task<GraphComputeTask.Result> {

    private final CourseGraph graph;
    private final double maxCreditsPerTerm;

    public GraphComputeTask(CourseGraph graph, double maxCreditsPerTerm) {
        this.graph = graph;
        this.maxCreditsPerTerm = maxCreditsPerTerm;
    }

    @Override
    protected Result call() {
        updateMessage("Computing statistics…");
        int total = graph.getAllCourses().size();
        int completed = graph.getCompletedCourses().size();
        int available = graph.getAvailableCourses().size();
        double totalCredits = graph.getAllCourses().stream()
                .mapToDouble(Course::getCredits).sum();

        updateProgress(1, 4);
        updateMessage("Detecting circular dependencies…");
        List<String> cycle = graph.detectCycle();

        updateProgress(2, 4);
        updateMessage("Generating semester plan…");
        List<SemesterPlan> plan;
        try {
            plan = graph.generateSemesterPlan(maxCreditsPerTerm);
        } catch (IllegalStateException ex) {
            // Cycle present – return empty plan; caller handles the cycle list
            plan = List.of();
        }

        updateProgress(3, 4);
        updateMessage("Done.");
        updateProgress(4, 4);

        return new Result(total, completed, available, totalCredits, cycle, plan);
    }

    // ─── Result record ──────────────────────────────────────────────────────

    /** Immutable snapshot of all computed graph data. */
    public static final class Result {
        public final int total;
        public final int completed;
        public final int available;
        public final double totalCredits;
        public final List<String> cyclePath;   // empty if no cycle
        public final List<SemesterPlan> semesterPlan;

        Result(int total, int completed, int available,
               double totalCredits, List<String> cyclePath,
               List<SemesterPlan> semesterPlan) {
            this.total = total;
            this.completed = completed;
            this.available = available;
            this.totalCredits = totalCredits;
            this.cyclePath = cyclePath;
            this.semesterPlan = semesterPlan;
        }

        public boolean hasCycle() {
            return !cyclePath.isEmpty();
        }
    }
}
