package com.prerequix.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Singleton that owns {@code prerequix.db}, creates the full 6-table schema on
 * first run, and supplies repository instances to the rest of the application.
 *
 * <p><b>Schema tables:</b>
 * <ul>
 *   <li>{@code courses}            – master course catalogue (shared)</li>
 *   <li>{@code prerequisites}      – prerequisite graph edges (junction)</li>
 *   <li>{@code curricula}          – predefined curriculum templates</li>
 *   <li>{@code curriculum_courses} – courses per template (junction)</li>
 *   <li>{@code student_curriculum} – which template each student chose</li>
 *   <li>{@code student_course_list}– each student's personal course set + status</li>
 * </ul>
 */
public class DatabaseManager {

    private static final String DB_FILENAME = "prerequix.db";
    private static DatabaseManager instance;

    private final String dbUrl;
    private final SQLiteCourseRepository courseRepo;
    private final StudentCurriculumRepository studentRepo;

    private DatabaseManager() {
        Path dbPath = Paths.get(System.getProperty("user.dir"), DB_FILENAME);
        dbUrl      = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        courseRepo  = new SQLiteCourseRepository(dbUrl);
        studentRepo = new StudentCurriculumRepository(dbUrl);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    /** Creates all 6 tables if they do not exist. Safe to call on every startup. */
    public void initialize() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement  st   = conn.createStatement()) {

            st.execute("PRAGMA foreign_keys = ON");

            st.execute("""
                CREATE TABLE IF NOT EXISTS courses (
                    id          TEXT PRIMARY KEY,
                    code        TEXT NOT NULL,
                    title       TEXT NOT NULL,
                    credits     REAL NOT NULL DEFAULT 3.0,
                    department  TEXT,
                    description TEXT,
                    status      TEXT NOT NULL DEFAULT 'UNCOMPLETED'
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS prerequisites (
                    course_id       TEXT NOT NULL,
                    prerequisite_id TEXT NOT NULL,
                    PRIMARY KEY (course_id, prerequisite_id),
                    FOREIGN KEY (course_id)       REFERENCES courses(id) ON DELETE CASCADE,
                    FOREIGN KEY (prerequisite_id) REFERENCES courses(id) ON DELETE CASCADE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS curricula (
                    id            TEXT PRIMARY KEY,
                    name          TEXT NOT NULL,
                    department    TEXT NOT NULL,
                    degree        TEXT NOT NULL,
                    description   TEXT,
                    total_courses INTEGER DEFAULT 0
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS curriculum_courses (
                    curriculum_id TEXT NOT NULL,
                    course_id     TEXT NOT NULL,
                    PRIMARY KEY (curriculum_id, course_id),
                    FOREIGN KEY (curriculum_id) REFERENCES curricula(id)  ON DELETE CASCADE,
                    FOREIGN KEY (course_id)     REFERENCES courses(id)    ON DELETE CASCADE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS student_curriculum (
                    student_id    TEXT PRIMARY KEY,
                    curriculum_id TEXT NOT NULL,
                    chosen_at     TEXT NOT NULL,
                    FOREIGN KEY (curriculum_id) REFERENCES curricula(id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS student_course_list (
                    student_id TEXT NOT NULL,
                    course_id  TEXT NOT NULL,
                    status     TEXT NOT NULL DEFAULT 'UNCOMPLETED',
                    PRIMARY KEY (student_id, course_id),
                    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                )""");
        }
        System.out.println("[DB] Initialized: " + dbUrl);
    }

    /** Course CRUD repository (master catalogue). */
    public SQLiteCourseRepository repository() { return courseRepo; }

    /** Student curriculum / course-list repository. */
    public StudentCurriculumRepository studentRepository() { return studentRepo; }

    public String getDbUrl() { return dbUrl; }
}