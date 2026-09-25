package com.prerequix.concurrent;

import com.prerequix.db.CourseRepository;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Background {@link Task} that persists the entire in-memory {@link CourseGraph}
 * to the SQLite database.
 *
 * <p>Replaces the old JSON-based {@code SaveDataTask}.
 * Uses {@link CourseRepository#updateCourse(Course)} (INSERT OR REPLACE) so
 * that the call is idempotent — it safely handles both first-time inserts and
 * subsequent updates without needing to know which courses are new vs. existing.
 */
public class DatabaseSaveTask extends Task<Void> {

    private final CourseGraph graph;
    private final CourseRepository repository;

    public DatabaseSaveTask(CourseGraph graph, CourseRepository repository) {
        this.graph      = graph;
        this.repository = repository;
    }

    @Override
    protected Void call() throws Exception {
        List<Course> courses = new java.util.ArrayList<>(graph.getAllCourses());
        updateMessage("Saving " + courses.size() + " courses to database...");

        int saved = 0;
        for (Course c : courses) {
            // updateCourse uses INSERT OR REPLACE, safe for both new and existing rows
            repository.updateCourse(c);
            saved++;
            updateProgress(saved, courses.size());
        }

        updateMessage("Saved " + saved + " courses.");
        return null;
    }
}
