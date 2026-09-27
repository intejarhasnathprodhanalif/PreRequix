package com.prerequix.concurrent;

import com.prerequix.db.DatabaseManager;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Background {@link Task} that loads a student's personal course list from
 * the {@code student_course_list} table into the in-memory {@link CourseGraph}.
 */
public class StudentLoadTask extends Task<Void> {

    private final String studentId;
    private final CourseGraph graph;

    public StudentLoadTask(String studentId, CourseGraph graph) {
        this.studentId = studentId;
        this.graph     = graph;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Loading your course plan from database...");
        List<Course> courses = DatabaseManager.getInstance()
                .studentRepository().loadStudentCourses(studentId);

        updateMessage("Building prerequisite graph (" + courses.size() + " courses)...");
        for (Course c : courses) graph.addCourse(c);
        for (Course c : courses)
            for (String prereqId : c.getPrerequisiteIds())
                if (graph.hasCourse(prereqId))
                    graph.addPrerequisite(c.getId(), prereqId);

        updateMessage("Loaded " + courses.size() + " courses.");
        return null;
    }
}
