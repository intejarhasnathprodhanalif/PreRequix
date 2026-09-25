package com.prerequix.concurrent;

import com.prerequix.model.AcademicPlanResult;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Background {@link Task} that performs all expensive graph computations
 * on a non-UI thread and returns a {@link Result} snapshot.
 *
 * <p>Computation includes:
 * <ul>
 *   <li>Collecting stats (total, completed, available, credits)</li>
 *   <li>Running DFS cycle detection</li>
 *   <li>Generating the constrained academic plan (5 courses / term, max 120 credits total)</li>
 * </ul>
 *
 * Usage:
 * <pre>{@code
 *   GraphComputeTask task = new GraphComputeTask(graph);
 *   task.setOnSucceeded(e -> applyResult(task.getValue()));
 *   task.setOnFailed(e -> handleError(task.getException()));
 *   AppExecutor.getInstance().computePool().submit(task);
 * }</pre>
 */
public class GraphComputeTask extends Task<GraphComputeTask.Result> {

    /** Number of courses per term (hard academic rule). */
    public static final int    COURSES_PER_TERM    = 5;
    /** Maximum total credits across all planned terms (hard academic rule). */
    public static final double MAX_TOTAL_CREDITS   = 120.0;

    private final CourseGraph graph;

    public GraphComputeTask(CourseGraph graph) {
        this.graph = graph;
    }

    /** Legacy constructor kept for compatibility — ignores maxCreditsPerTerm. */
    public GraphComputeTask(CourseGraph graph, double ignoredMaxCreditsPerTerm) {
        this.graph = graph;
    }

    @Override
    protected Result call() {
        updateMessage("Computing statistics...");
        int    total        = graph.getAllCourses().size();
        int    completed    = graph.getCompletedCourses().size();
        int    available    = graph.getAvailableCourses().size();
        double totalCredits = graph.getAllCourses().stream()
                                   .mapToDouble(Course::getCredits).sum();

        updateProgress(1, 4);
        updateMessage("Detecting circular dependencies...");
        List<String> cycle = graph.detectCycle();

        updateProgress(2, 4);
        updateMessage("Generating academic plan (5 courses / term, max 120 credits)...");

        AcademicPlanResult planResult;
        if (!cycle.isEmpty()) {
            planResult = new AcademicPlanResult(List.of(), List.of());
        } else {
            try {
                planResult = graph.generateAcademicPlan(COURSES_PER_TERM, MAX_TOTAL_CREDITS);
            } catch (IllegalStateException ex) {
                planResult = new AcademicPlanResult(List.of(), List.of());
            }
        }

        updateProgress(3, 4);
        updateMessage("Done.");
        updateProgress(4, 4);

        return new Result(total, completed, available, totalCredits, cycle, planResult);
    }

    // ─── Result record ───────────────────────────────────────────────────

    /** Immutable snapshot of all computed graph data. */
    public static final class Result {
        public final int    total;
        public final int    completed;
        public final int    available;
        public final double totalCredits;
        public final List<String>      cyclePath;
        public final AcademicPlanResult academicPlan;

        Result(int total, int completed, int available,
               double totalCredits, List<String> cyclePath,
               AcademicPlanResult academicPlan) {
            this.total        = total;
            this.completed    = completed;
            this.available    = available;
            this.totalCredits = totalCredits;
            this.cyclePath    = cyclePath;
            this.academicPlan = academicPlan;
        }

        public boolean hasCycle() { return !cyclePath.isEmpty(); }

        /** Convenience: planned semester list. */
        public java.util.List<com.prerequix.model.SemesterPlan> semesterPlan() {
            return academicPlan.plannedTerms;
        }
    }
}