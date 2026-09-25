package com.prerequix.concurrent;

import com.prerequix.db.CourseRepository;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Background {@link Task} that loads all courses from the SQLite database
 * into the in-memory {@link CourseGraph}.
 *
 * <p>Replaces the old JSON-based {@code LoadDataTask}.
 * Runs on the {@link AppExecutor#ioExecutor()} thread to avoid blocking the UI.
 */
public class DatabaseLoadTask extends Task<Void> {

    private final CourseGraph graph;
    private final CourseRepository repository;

    public DatabaseLoadTask(CourseGraph graph, CourseRepository repository) {
        this.graph      = graph;
        this.repository = repository;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Loading courses from database...");
        List<Course> courses = repository.getAllCourses();

        updateMessage("Building prerequisite graph (" + courses.size() + " courses)...");
        // First pass: add all courses
        for (Course c : courses) {
            graph.addCourse(c);
        }
        // Second pass: wire prerequisites (all courses already in graph)
        for (Course c : courses) {
            for (String prereqId : c.getPrerequisiteIds()) {
                if (graph.hasCourse(prereqId)) {
                    graph.addPrerequisite(c.getId(), prereqId);
                }
            }
        }

        updateMessage("Loaded " + courses.size() + " courses from database.");
        return null;
    }
}
