package com.prerequix.model;

/**
 * Enum representing the progress status of a course.
 */
public enum CourseStatus {
    UNCOMPLETED("Locked / Not Taken", "status-uncompleted"),
    IN_PROGRESS("In Progress", "status-in-progress"),
    COMPLETED("Completed", "status-completed");

    private final String displayName;
    private final String cssClass;

    CourseStatus(String displayName, String cssClass) {
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCssClass() {
        return cssClass;
    }
}
