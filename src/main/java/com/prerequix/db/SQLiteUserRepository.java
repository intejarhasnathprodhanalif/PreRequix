package com.prerequix.db;

import com.prerequix.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * SQLite implementation of {@link UserRepository}.
 *
 * <p>Stores all user records in a dedicated {@code users.db} file
 * (separate from {@code prerequix.db} which holds course data).
 *
 * <p>Password security: plain-text passwords are <b>never</b> stored.
 * Before every write the password is hashed with SHA-256 and only the
 * hex digest is persisted.
 *
 * <p>Student ID format: {@code PRQ-XXXXX} (5 random digits, unique).
 */
public class SQLiteUserRepository implements UserRepository {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String dbUrl;

    public SQLiteUserRepository(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    // ── Connection helper ─────────────────────────────────────────────────

    private Connection getConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(dbUrl);
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    @Override
    public User register(String fullName, String username, String password) throws Exception {
        if (fullName == null || fullName.isBlank())
            throw new IllegalArgumentException("Full name must not be blank.");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username must not be blank.");
        if (password == null || password.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (usernameExists(username))
            throw new IllegalArgumentException("Username \"" + username + "\" is already taken.");

        String id          = UUID.randomUUID().toString();
        String studentId   = generateUniqueStudentId();
        String hash        = hashPassword(password);
        String createdAt   = LocalDateTime.now().format(TS_FMT);

        String sql = """
                INSERT INTO users (id, student_id, username, full_name, password_hash, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, studentId);
            ps.setString(3, username.trim().toLowerCase());
            ps.setString(4, fullName.trim());
            ps.setString(5, hash);
            ps.setString(6, createdAt);
            ps.executeUpdate();
        }
        return new User(id, studentId, username.trim().toLowerCase(),
                        fullName.trim(), hash, createdAt);
    }

    // ── READ / AUTHENTICATE ───────────────────────────────────────────────

    @Override
    public User login(String username, String password) throws Exception {
        if (username == null || password == null)
            throw new IllegalArgumentException("Username and password are required.");

        User user = findByUsername(username.trim().toLowerCase());
        if (user == null)
            throw new IllegalArgumentException("No account found for username \"" + username + "\".");

        if (!user.getPasswordHash().equals(hashPassword(password)))
            throw new IllegalArgumentException("Incorrect password.");

        return user;
    }

    @Override
    public User findByUsername(String username) throws Exception {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public User findById(String id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public boolean usernameExists(String username) throws Exception {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    @Override
    public void updatePassword(String userId, String newPassword) throws Exception {
        if (newPassword == null || newPassword.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashPassword(newPassword));
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    @Override
    public void deleteUser(String userId) throws Exception {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    /**
     * Hashes a plain-text password with SHA-256.
     * Returns the lowercase hex string of the digest.
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Generates a unique {@code PRQ-XXXXX} student ID, retrying on collision. */
    private String generateUniqueStudentId() throws Exception {
        Random rng = new Random();
        for (int attempt = 0; attempt < 20; attempt++) {
            String sid = "PRQ-" + String.format("%05d", rng.nextInt(100_000));
            String sql = "SELECT 1 FROM users WHERE student_id = ?";
            try (Connection conn = getConnection();
                 var ps = conn.prepareStatement(sql)) {
                ps.setString(1, sid);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) return sid;   // unique — use it
                }
            }
        }
        throw new RuntimeException("Could not generate a unique student ID after 20 attempts.");
    }

    private User mapRow(java.sql.ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("student_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                rs.getString("created_at")
        );
    }
}
