package com.prerequix.concurrent;

import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Background {@link Task} that performs course search and filter operations
 * off the JavaFX Application Thread.
 *
 * <p>Accepts a query string and an optional status filter key and returns
 * the filtered {@link List} of {@link Course} objects.</p>
 *
 * Filter keys: {@code "ALL"}, {@code "COMPLETED"}, {@code "IN_PROGRESS"},
 *              {@code "UNCOMPLETED"}, {@code "AVAILABLE"}.
 */
public class SearchFilterTask extends Task<List<Course>> {

    private final CourseGraph graph;
    private final String searchText;  // may be empty – means no text filter
    private final String filterKey;   // status category

    public SearchFilterTask(CourseGraph graph, String searchText, String filterKey) {
        this.graph = graph;
        this.searchText = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        this.filterKey = filterKey == null ? "ALL" : filterKey;
    }

    @Override
    protected List<Course> call() {
        List<Course> result = new ArrayList<>();

        for (Course c : graph.getAllCourses()) {
            if (isCancelled()) break;

            // ── Text filter ──────────────────────────────────────────────
            if (!searchText.isEmpty()) {
                boolean match =
                        c.getCode().toLowerCase(Locale.ROOT).contains(searchText)
                        || c.getTitle().toLowerCase(Locale.ROOT).contains(searchText)
                        || (c.getDepartment() != null
                            && c.getDepartment().toLowerCase(Locale.ROOT).contains(searchText));
                if (!match) continue;
            }

            // ── Category filter ──────────────────────────────────────────
            switch (filterKey) {
                case "COMPLETED":
                    if (!c.isCompleted()) continue;
                    break;
                case "IN_PROGRESS":
                    if (!c.isInProgress()) continue;
                    break;
                case "UNCOMPLETED":
                    if (c.isCompleted() || graph.isCourseAvailable(c.getId())) continue;
                    break;
                case "AVAILABLE":
                    if (!graph.isCourseAvailable(c.getId())) continue;
                    break;
                default: // "ALL" – no extra filtering
                    break;
            }

            result.add(c);
        }

        return result;
    }
}
