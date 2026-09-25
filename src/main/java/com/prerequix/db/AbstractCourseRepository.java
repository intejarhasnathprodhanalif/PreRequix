package com.prerequix.db;

import com.prerequix.model.Course;
import com.prerequix.model.CourseStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class (Abstract Class) providing shared validation and
 * helper utilities common to all {@link CourseRepository} implementations.
 *
 * <p><b>Advanced OOP:</b> This abstract class sits between the {@link CourseRepository}
 * interface and the concrete {@link SQLiteCourseRepository}, demonstrating the
 * Template Method pattern: common pre/post-condition checks live here while
 * subclasses supply the actual data-access mechanism via {@link #getConnection()}.
 *
 * <p>Concrete subclasses MUST implement:
 * <ul>
 *   <li>{@link #getConnection()} – supply an open JDBC connection</li>
 *   <li>All CRUD methods from {@link CourseRepository}</li>
 * </ul>
 */
public abstract class AbstractCourseRepository implements CourseRepository {

    // ── Template method: subclasses supply the connection ─────────────────
    /**
     * Returns an open database connection.
     * Concrete implementations decide the backend (SQLite, H2, …).
     */
    protected abstract Connection getConnection() throws SQLException;

    // ── Shared validation helpers ─────────────────────────────────────────

    /**
     * Validates a {@link Course} before any persistence operation.
     *
     * @throws IllegalArgumentException if required fields are blank
     */
    protected void validateCourse(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null.");
        }
        if (course.getCode() == null || course.getCode().isBlank()) {
            throw new IllegalArgumentException("Course code must not be blank.");
        }
        if (course.getTitle() == null || course.getTitle().isBlank()) {
            throw new IllegalArgumentException("Course title must not be blank.");
        }
        if (course.getCredits() <= 0) {
            throw new IllegalArgumentException("Credits must be positive.");
        }
    }

    /**
     * Validates a course ID string.
     *
     * @throws IllegalArgumentException if id is null or blank
     */
    protected void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Course ID must not be blank.");
        }
    }

    /**
     * Maps a status string stored in the DB back to a {@link CourseStatus} enum,
     * defaulting to {@code UNCOMPLETED} for any unknown value.
     */
    protected CourseStatus parseStatus(String raw) {
        if (raw == null) return CourseStatus.UNCOMPLETED;
        return switch (raw.toUpperCase()) {
            case "COMPLETED"    -> CourseStatus.COMPLETED;
            case "IN_PROGRESS"  -> CourseStatus.IN_PROGRESS;
            default             -> CourseStatus.UNCOMPLETED;
        };
    }

    /**
     * Executes a block of SQL work inside a managed transaction.
     * Commits on success, rolls back on any exception.
     */
    protected void inTransaction(SqlWork work) throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.execute(conn);
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    /** Functional interface for transactional SQL work. */
    @FunctionalInterface
    protected interface SqlWork {
        void execute(Connection conn) throws Exception;
    }
}
