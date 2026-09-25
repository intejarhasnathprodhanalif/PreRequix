package com.prerequix.db;

import com.prerequix.model.Course;

import java.util.List;
import java.util.Set;

/**
 * Repository contract (Interface) for all course data persistence operations.
 *
 * <p>Every method maps directly to one of the four CRUD operations:
 * <ul>
 *   <li><b>Create</b> – {@link #saveCourse(Course)}</li>
 *   <li><b>Read</b>   – {@link #getAllCourses()}, {@link #getCourse(String)}</li>
 *   <li><b>Update</b> – {@link #updateCourse(Course)}</li>
 *   <li><b>Delete</b> – {@link #deleteCourse(String)}</li>
 * </ul>
 *
 * <p>Prerequisite relationships are stored separately in a junction table
 * and managed via {@link #savePrerequisites(String, Set)}.
 *
 * <p><b>Advanced OOP:</b> This interface defines the abstraction boundary.
 * Any storage backend (SQLite, in-memory, cloud) can be plugged in by
 * implementing this interface without touching the rest of the application.
 */
public interface CourseRepository {

    /** Persist a new course record (CREATE). */
    void saveCourse(Course course) throws Exception;

    /** Retrieve all stored courses (READ). */
    List<Course> getAllCourses() throws Exception;

    /** Retrieve a single course by its ID (READ). Returns {@code null} if not found. */
    Course getCourse(String id) throws Exception;

    /**
     * Overwrite an existing course record (UPDATE).
     * Also replaces the course's prerequisite rows.
     */
    void updateCourse(Course course) throws Exception;

    /** Remove a course and all its prerequisite links (DELETE). */
    void deleteCourse(String id) throws Exception;

    /**
     * Replace the entire set of prerequisite IDs for {@code courseId}.
     * Called after every {@link #saveCourse} and {@link #updateCourse}.
     */
    void savePrerequisites(String courseId, Set<String> prerequisiteIds) throws Exception;

    /** Retrieve the set of prerequisite IDs for a given course. */
    Set<String> getPrerequisites(String courseId) throws Exception;

    /** Remove all data – used for testing and reset. */
    void clearAll() throws Exception;
}
