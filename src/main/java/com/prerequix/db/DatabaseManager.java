package com.prerequix.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Singleton that owns the database file path, creates the schema on first run,
 * and supplies the application-wide {@link SQLiteCourseRepository}.
 *
 * <p>Call {@link #initialize()} once at application startup (in {@code App.start()}).
 * After that every component calls {@link #repository()} to obtain the repository.
 */
public class DatabaseManager {

    private static final String DB_FILENAME = "prerequix.db";
    private static DatabaseManager instance;

    private final String dbUrl;
    private final SQLiteCourseRepository repository;

    private DatabaseManager() {
        // Store the .db file next to the JAR / working directory
        Path dbPath = Paths.get(System.getProperty("user.dir"), DB_FILENAME);
        dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        repository = new SQLiteCourseRepository(dbUrl);
    }

    /** Returns the singleton instance (thread-safe, lazy-init). */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    /**
     * Creates the {@code courses} and {@code prerequisites} tables if they do
     * not already exist, and enables foreign-key enforcement.
     *
     * <p><b>Schema:</b>
     * <pre>
     * courses
     *   id          TEXT  PRIMARY KEY
     *   code        TEXT  NOT NULL
     *   title       TEXT  NOT NULL
     *   credits     REAL  NOT NULL DEFAULT 3.0
     *   department  TEXT
     *   description TEXT
     *   status      TEXT  NOT NULL DEFAULT 'UNCOMPLETED'
     *
     * prerequisites          (junction table – many-to-many)
     *   course_id       TEXT NOT NULL  REFERENCES courses(id) ON DELETE CASCADE
     *   prerequisite_id TEXT NOT NULL  REFERENCES courses(id) ON DELETE CASCADE
     *   PRIMARY KEY (course_id, prerequisite_id)
     * </pre>
     */
    public void initialize() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement st = conn.createStatement()) {

            // Enable foreign-key support (SQLite requires explicit activation)
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
                    )
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS prerequisites (
                        course_id       TEXT NOT NULL,
                        prerequisite_id TEXT NOT NULL,
                        PRIMARY KEY (course_id, prerequisite_id),
                        FOREIGN KEY (course_id)
                            REFERENCES courses(id) ON DELETE CASCADE,
                        FOREIGN KEY (prerequisite_id)
                            REFERENCES courses(id) ON DELETE CASCADE
                    )
                    """);
        }
        System.out.println("[DB] Initialized: " + dbUrl);
    }

    /** Returns the application-wide repository instance. */
    public SQLiteCourseRepository repository() {
        return repository;
    }

    /** Exposes the raw JDBC URL (useful for debugging / testing). */
    public String getDbUrl() {
        return dbUrl;
    }
}
