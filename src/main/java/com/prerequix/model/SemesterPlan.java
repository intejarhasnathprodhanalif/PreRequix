package com.prerequix.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single term/semester schedule in a generated sequence plan.
 */
public class SemesterPlan {
    private int termNumber;
    private List<Course> courses;
    private double totalCredits;

    public SemesterPlan(int termNumber) {
        this.termNumber = termNumber;
        this.courses = new ArrayList<>();
        this.totalCredits = 0.0;
    }

    /** Convenience constructor for building plans from a pre-existing course list. */
    public SemesterPlan(int termNumber, List<Course> courses) {
        this.termNumber = termNumber;
        this.courses = new ArrayList<>(courses);
        this.totalCredits = courses.stream().mapToDouble(Course::getCredits).sum();
    }

    public int getTermNumber() {
        return termNumber;
    }

    public List<Course> getCourses() {
        return courses;
    }

    public double getTotalCredits() {
        return totalCredits;
    }

    public void addCourse(Course course) {
        if (course != null) {
            courses.add(course);
            totalCredits += course.getCredits();
        }
    }

    public boolean canFitCourse(Course course, double maxCreditsPerTerm) {
        if (course == null) return false;
        if (courses.isEmpty()) return true; // At least one course per term if available
        return (totalCredits + course.getCredits()) <= maxCreditsPerTerm;
    }
}
