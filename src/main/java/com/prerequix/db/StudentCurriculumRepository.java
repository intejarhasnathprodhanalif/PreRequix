package com.prerequix.db;

import com.prerequix.model.Course;
import com.prerequix.model.CourseStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for per-student curriculum operations:
 * choosing a curriculum template, loading a student's personal course list,
 * and saving edits back to the database.
 */
public class StudentCurriculumRepository {

    private final String dbUrl;

    public StudentCurriculumRepository(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    // ── Curriculum templates ──────────────────────────────────────────────

    /** Returns all available curriculum templates. */
    public List<CurriculumInfo> getAllCurricula() throws Exception {
        List<CurriculumInfo> list = new ArrayList<>();
        String sql = "SELECT id, name, department, degree, description, total_courses FROM curricula ORDER BY name";
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new CurriculumInfo(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getString("degree"),
                        rs.getString("description"),
                        rs.getInt("total_courses")));
            }
        }
        return list;
    }

    /** Returns course previews for a curriculum template (for picker dialog). */
    public List<Course> getCurriculumCourses(String curriculumId) throws Exception {
        List<Course> courses = new ArrayList<>();
        String sql = """
                SELECT c.* FROM courses c
                JOIN curriculum_courses cc ON cc.course_id = c.id
                WHERE cc.curriculum_id = ?
                ORDER BY c.code""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, curriculumId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) courses.add(mapCourse(rs));
            }
        }
        return courses;
    }

    // ── Student curriculum choice ─────────────────────────────────────────

    /** Returns the curriculum ID the student chose, or null if none. */
    public String getStudentCurriculumId(String studentId) throws Exception {
        String sql = "SELECT curriculum_id FROM student_curriculum WHERE student_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("curriculum_id");
            }
        }
        return null;
    }

    /**
     * Records the student's curriculum choice and initialises their
     * personal course list from the template (UNCOMPLETED status for all).
     */
    public void chooseAndInitCurriculum(String studentId, String curriculumId) throws Exception {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Record choice
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR REPLACE INTO student_curriculum (student_id, curriculum_id, chosen_at) VALUES (?, ?, ?)")) {
                    ps.setString(1, studentId);
                    ps.setString(2, curriculumId);
                    ps.setString(3, ts);
                    ps.executeUpdate();
                }
                // Seed student_course_list from the template
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO student_course_list (student_id, course_id, status) " +
                        "SELECT ?, course_id, 'UNCOMPLETED' FROM curriculum_courses WHERE curriculum_id = ?")) {
                    ps.setString(1, studentId);
                    ps.setString(2, curriculumId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) { conn.rollback(); throw e; }
        }
    }

    // ── Student personal course list ──────────────────────────────────────

    /** Loads the student's personal course list with their completion statuses. */
    public List<Course> loadStudentCourses(String studentId) throws Exception {
        List<Course> courses = new ArrayList<>();
        String sql = """
                SELECT c.*, scl.status AS student_status
                FROM courses c
                JOIN student_course_list scl ON scl.course_id = c.id
                WHERE scl.student_id = ?
                ORDER BY c.code""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Course c = mapCourse(rs);
                    c.setStatus(parseStatus(rs.getString("student_status")));
                    courses.add(c);
                }
            }
        }
        // Attach prerequisites (only within this student's set)
        for (Course c : courses) {
            String preqSql = """
                    SELECT p.prerequisite_id FROM prerequisites p
                    JOIN student_course_list scl ON scl.course_id = p.prerequisite_id
                    WHERE p.course_id = ? AND scl.student_id = ?""";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(preqSql)) {
                ps.setString(1, c.getId());
                ps.setString(2, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) c.getPrerequisiteIds().add(rs.getString(1));
                }
            }
        }
        return courses;
    }

    /**
     * Saves the student's current course list back to the DB.
     * New courses (not in master {@code courses} table) are inserted first.
     */
    public void saveStudentCourses(String studentId, List<Course> courses) throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1) Upsert all courses into master catalogue
                String upsertCourse = """
                        INSERT OR REPLACE INTO courses
                            (id, code, title, credits, department, description, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'UNCOMPLETED')""";
                try (PreparedStatement ps = conn.prepareStatement(upsertCourse)) {
                    for (Course c : courses) {
                        ps.setString(1, c.getId());
                        ps.setString(2, c.getCode());
                        ps.setString(3, c.getTitle());
                        ps.setDouble(4, c.getCredits());
                        ps.setString(5, c.getDepartment());
                        ps.setString(6, c.getDescription());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // 2) Upsert prerequisites
                String upsertPrereq = "INSERT OR IGNORE INTO prerequisites (course_id, prerequisite_id) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(upsertPrereq)) {
                    for (Course c : courses) {
                        for (String prereqId : c.getPrerequisiteIds()) {
                            ps.setString(1, c.getId());
                            ps.setString(2, prereqId);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                // 3) Refresh student_course_list
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM student_course_list WHERE student_id = ?")) {
                    del.setString(1, studentId);
                    del.executeUpdate();
                }
                String insertList = "INSERT INTO student_course_list (student_id, course_id, status) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertList)) {
                    for (Course c : courses) {
                        ps.setString(1, studentId);
                        ps.setString(2, c.getId());
                        ps.setString(3, c.getStatus().name());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (Exception e) { conn.rollback(); throw e; }
        }
    }

    /** Checks whether the curricula table has been seeded. */
    public boolean isCurriculaSeeded() throws Exception {
        String sql = "SELECT COUNT(*) FROM curricula";
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Course mapCourse(ResultSet rs) throws SQLException {
        return new Course(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getDouble("credits"),
                rs.getString("department"));
    }

    private CourseStatus parseStatus(String raw) {
        if (raw == null) return CourseStatus.UNCOMPLETED;
        return switch (raw.toUpperCase()) {
            case "COMPLETED"   -> CourseStatus.COMPLETED;
            case "IN_PROGRESS" -> CourseStatus.IN_PROGRESS;
            default            -> CourseStatus.UNCOMPLETED;
        };
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────

    public record CurriculumInfo(
            String id, String name, String department,
            String degree, String description, int totalCourses) {}
}
