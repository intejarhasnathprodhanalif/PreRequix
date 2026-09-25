package com.prerequix.concurrent;

import com.prerequix.model.Course;
import com.prerequix.net.CourseApiClient;
import com.prerequix.net.RemoteCourseParser;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Background {@link Task} that performs the full network fetch + JSON parse
 * cycle for remote course data.
 *
 * <p>Sequence:
 * <ol>
 *   <li>HTTP GET to the configured URL (via {@link CourseApiClient}).</li>
 *   <li>Parse the raw JSON body (via {@link RemoteCourseParser}).</li>
 *   <li>Return the parsed {@link List}&lt;{@link Course}&gt; to the UI thread.</li>
 * </ol>
 *
 * <p>The result is delivered to the JavaFX Application Thread via
 * {@code Task.setOnSucceeded()} in {@code MainController}, which then
 * opens the import preview dialog.
 *
 * <p>Runs on the {@link AppExecutor#computePool()} — network I/O is fast enough
 * and we do not want to block the single-thread I/O executor.
 */
public class FetchCoursesTask extends Task<FetchCoursesTask.FetchResult> {

    private final String url;
    private final CourseApiClient apiClient;
    private final RemoteCourseParser parser;

    public FetchCoursesTask(String url) {
        this.url       = url;
        this.apiClient = new CourseApiClient();
        this.parser    = new RemoteCourseParser();
    }

    @Override
    protected FetchResult call() throws Exception {
        updateMessage("Connecting to " + url + " ...");
        String rawJson = apiClient.fetchJson(url);

        updateMessage("Parsing course data...");
        List<Course> courses   = parser.parse(rawJson);
        String       sourceInfo = parser.parseSourceInfo(rawJson);

        updateMessage("Fetched " + courses.size() + " courses from remote.");
        return new FetchResult(courses, sourceInfo, rawJson);
    }

    // ── Result record ─────────────────────────────────────────────────────

    /** Immutable bundle returned to the UI thread on success. */
    public record FetchResult(
            List<Course> courses,
            String sourceInfo,
            String rawJson) {}
}
