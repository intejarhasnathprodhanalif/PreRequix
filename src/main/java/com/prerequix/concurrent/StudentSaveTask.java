package com.prerequix.concurrent;

import com.prerequix.db.DatabaseManager;
import com.prerequix.model.Course;
import com.prerequix.model.CourseGraph;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Background {@link Task} that saves the student's current {@link CourseGraph}
 * state back to the database — updating both the master {@code courses} table
 * (for any new courses added by the student) and the {@code student_course_list}.
 */
public class StudentSaveTask extends Task<Void> {

    private final String studentId;
    private final CourseGraph graph;

    public StudentSaveTask(String studentId, CourseGraph graph) {
        this.studentId = studentId;
        this.graph     = graph;
    }

    @Override
    protected Void call() throws Exception {
        List<Course> courses = new ArrayList<>(graph.getAllCourses());
        updateMessage("Saving your plan (" + courses.size() + " courses)...");
        DatabaseManager.getInstance().studentRepository()
                .saveStudentCourses(studentId, courses);
        updateProgress(1, 1);
        updateMessage("Plan saved.");
        return null;
    }
}
