package com.prerequix.db;

import com.prerequix.model.Course;
import com.prerequix.model.CourseStatus;

import java.sql.*;
import java.util.*;

/**
 * Concrete SQLite implementation of {@link AbstractCourseRepository}.
 *
 * <p>Uses the JDBC API with the Xerial SQLite driver.
 * All SQL operations run through the transactional helper provided by the
 * abstract base class, ensuring atomicity for multi-step writes.
 *
 * <p><b>CRUD Mapping:</b>
 * <ul>
 *   <li>CREATE  – {@link #saveCourse(Course)} + {@link #savePrerequisites}</li>
 *   <li>READ    – {@link #getAllCourses()}, {@link #getCourse(String)}, {@link #getPrerequisites}</li>
 *   <li>UPDATE  – {@link #updateCourse(Course)}</li>
 *   <li>DELETE  – {@link #deleteCourse(String)}</li>
 * </ul>
 */
public class SQLiteCourseRepository extends AbstractCourseRepository {

    private final String dbUrl;

    public SQLiteCourseRepository(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    // ── Template method implementation ────────────────────────────────────

    @Override
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    /**
     * Inserts a new course row and its prerequisite rows (CREATE).
     * Uses INSERT OR IGNORE to silently skip duplicate inserts.
     */
    @Override
    public void saveCourse(Course course) throws Exception {
        validateCourse(course);
        inTransaction(conn -> {
            String sql = """
                    INSERT OR IGNORE INTO courses
                        (id, code, title, credits, department, description, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, course.getId());
                ps.setString(2, course.getCode());
                ps.setString(3, course.getTitle());
                ps.setDouble(4, course.getCredits());
                ps.setString(5, course.getDepartment());
                ps.setString(6, course.getDescription());
                ps.setString(7, course.getStatus().name());
                ps.executeUpdate();
            }
            savePrerequisitesInConn(conn, course.getId(), course.getPrerequisiteIds());
        });
    }

    // ── READ ──────────────────────────────────────────────────────────────

    /** Retrieves ALL course rows plus their prerequisites (READ). */
    @Override
    public List<Course> getAllCourses() throws Exception {
        List<Course> courses = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM courses ORDER BY code")) {
            while (rs.next()) {
                courses.add(mapRow(rs));
            }
        }
        // Attach prerequisites
        for (Course c : courses) {
            c.getPrerequisiteIds().addAll(getPrerequisites(c.getId()));
        }
        return courses;
    }

    /** Retrieves a single course by primary key (READ). */
    @Override
    public Course getCourse(String id) throws Exception {
        validateId(id);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM courses WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Course c = mapRow(rs);
                    c.getPrerequisiteIds().addAll(getPrerequisites(id));
                    return c;
                }
            }
        }
        return null;
    }

    /** Returns the set of prerequisite IDs for {@code courseId} (READ). */
    @Override
    public Set<String> getPrerequisites(String courseId) throws Exception {
        Set<String> ids = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT prerequisite_id FROM prerequisites WHERE course_id = ?")) {
            ps.setString(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    /**
     * Replaces an existing course row and refreshes its prerequisites (UPDATE).
     * Uses INSERT OR REPLACE to handle both insert-on-conflict and full update.
     */
    @Override
    public void updateCourse(Course course) throws Exception {
        validateCourse(course);
        inTransaction(conn -> {
            String sql = """
                    INSERT OR REPLACE INTO courses
                        (id, code, title, credits, department, description, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, course.getId());
                ps.setString(2, course.getCode());
                ps.setString(3, course.getTitle());
                ps.setDouble(4, course.getCredits());
                ps.setString(5, course.getDepartment());
                ps.setString(6, course.getDescription());
                ps.setString(7, course.getStatus().name());
                ps.executeUpdate();
            }
            // Delete old prerequisite rows then re-insert
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM prerequisites WHERE course_id = ?")) {
                del.setString(1, course.getId());
                del.executeUpdate();
            }
            savePrerequisitesInConn(conn, course.getId(), course.getPrerequisiteIds());
        });
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    /**
     * Removes the course row; CASCADE deletes its prerequisite rows (DELETE).
     */
    @Override
    public void deleteCourse(String id) throws Exception {
        validateId(id);
        inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM courses WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        });
    }

    // ── Prerequisite helpers ──────────────────────────────────────────────

    /**
     * Replaces all prerequisite rows for a course (atomic; uses existing connection).
     */
    @Override
    public void savePrerequisites(String courseId, Set<String> prerequisiteIds) throws Exception {
        inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM prerequisites WHERE course_id = ?")) {
                del.setString(1, courseId);
                del.executeUpdate();
            }
            savePrerequisitesInConn(conn, courseId, prerequisiteIds);
        });
    }

    /** Inner helper; re-uses an already-open connection. */
    private void savePrerequisitesInConn(Connection conn, String courseId,
                                          Set<String> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return;
        String sql = "INSERT OR IGNORE INTO prerequisites (course_id, prerequisite_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String prereqId : ids) {
                ps.setString(1, courseId);
                ps.setString(2, prereqId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    /** Deletes all rows from both tables (for reset / testing). */
    @Override
    public void clearAll() throws Exception {
        inTransaction(conn -> {
            conn.createStatement().executeUpdate("DELETE FROM prerequisites");
            conn.createStatement().executeUpdate("DELETE FROM courses");
        });
    }

    /** Maps a {@link ResultSet} row to a {@link Course} object. */
    private Course mapRow(ResultSet rs) throws SQLException {
        Course c = new Course(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getDouble("credits"),
                rs.getString("department")
        );
        c.setStatus(parseStatus(rs.getString("status")));
        return c;
    }
}
