package com.prerequix.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entity representing an academic course with prerequisite relationships.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Course {
    private String id;
    private String code;
    private String title;
    private String description;
    private double credits;
    private String department;
    private CourseStatus status;
    private String grade;
    private Set<String> prerequisiteIds;

    public Course() {
        this.prerequisiteIds = new HashSet<>();
        this.status = CourseStatus.UNCOMPLETED;
        this.credits = 3.0;
    }

    public Course(String id, String code, String title, String description, double credits, String department) {
        this();
        this.id = sanitizeId(id);
        this.code = code;
        this.title = title;
        this.description = description;
        this.credits = credits;
        this.department = department;
    }

    public Course(String id, String code, String title, String description, double credits, String department, CourseStatus status) {
        this(id, code, title, description, credits, department);
        this.status = status;
    }

    public static String sanitizeId(String input) {
        if (input == null) return "";
        return input.trim().toUpperCase().replaceAll("\\s+", "");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = sanitizeId(id);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getCredits() {
        return credits;
    }

    public void setCredits(double credits) {
        this.credits = credits;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Set<String> getPrerequisiteIds() {
        return prerequisiteIds;
    }

    public void setPrerequisiteIds(Set<String> prerequisiteIds) {
        this.prerequisiteIds = prerequisiteIds != null ? prerequisiteIds : new HashSet<>();
    }

    public void addPrerequisite(String prereqCourseId) {
        if (prereqCourseId != null && !prereqCourseId.isBlank()) {
            this.prerequisiteIds.add(sanitizeId(prereqCourseId));
        }
    }

    public void removePrerequisite(String prereqCourseId) {
        if (prereqCourseId != null) {
            this.prerequisiteIds.remove(sanitizeId(prereqCourseId));
        }
    }

    public boolean isCompleted() {
        return status == CourseStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == CourseStatus.IN_PROGRESS;
    }

    public boolean isUncompleted() {
        return status == CourseStatus.UNCOMPLETED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return Objects.equals(id, course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return code + " - " + title + " (" + credits + " cr)";
    }
}
