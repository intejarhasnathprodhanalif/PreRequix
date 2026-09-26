package com.prerequix.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Singleton that owns the <b>separate</b> {@code users.db} database file,
 * creates the student-user schema on first run, and supplies the
 * application-wide {@link SQLiteUserRepository}.
 *
 * <p>Kept intentionally separate from {@link DatabaseManager} (which manages
 * {@code prerequix.db}) so that course data and user account data live in
 * independent files and can be managed or backed up independently.
 *
 * <p><b>Schema — {@code users} table:</b>
 * <pre>
 * users
 *   id            TEXT  PRIMARY KEY        (UUID)
 *   student_id    TEXT  UNIQUE NOT NULL    (PRQ-XXXXX)
 *   username      TEXT  UNIQUE NOT NULL
 *   full_name     TEXT  NOT NULL
 *   password_hash TEXT  NOT NULL           (SHA-256 hex)
 *   created_at    TEXT  NOT NULL           (ISO-8601 timestamp)
 * </pre>
 *
 * <p>Call {@link #initialize()} once at application startup before showing
 * the authentication screen.
 */
public class UserDatabaseManager {

    private static final String DB_FILENAME = "users.db";
    private static UserDatabaseManager instance;

    private final String dbUrl;
    private final SQLiteUserRepository repository;

    private UserDatabaseManager() {
        Path dbPath = Paths.get(System.getProperty("user.dir"), DB_FILENAME);
        dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        repository = new SQLiteUserRepository(dbUrl);
    }

    /** Returns the singleton instance (thread-safe, lazy). */
    public static synchronized UserDatabaseManager getInstance() {
        if (instance == null) instance = new UserDatabaseManager();
        return instance;
    }

    /**
     * Creates the {@code users} table if it does not already exist.
     * Safe to call multiple times.
     */
    public void initialize() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement  st   = conn.createStatement()) {

            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id            TEXT PRIMARY KEY,
                        student_id    TEXT UNIQUE NOT NULL,
                        username      TEXT UNIQUE NOT NULL,
                        full_name     TEXT NOT NULL,
                        password_hash TEXT NOT NULL,
                        created_at    TEXT NOT NULL
                    )
                    """);
        }
        System.out.println("[UserDB] Initialized: " + dbUrl);
    }

    /** Returns the application-wide user repository. */
    public SQLiteUserRepository repository() {
        return repository;
    }

    /** Raw JDBC URL (for debugging / DemoRunner). */
    public String getDbUrl() {
        return dbUrl;
    }
}
