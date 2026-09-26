package com.prerequix.model;

/**
 * Entity representing a registered student user.
 *
 * <p>Passwords are never stored in plain text — only the SHA-256 hash
 * is persisted in the {@code users} table. The student ID is
 * auto-generated in the format {@code PRQ-XXXXX}.
 */
public class User {

    private String id;           // UUID primary key
    private String studentId;    // e.g. PRQ-47291 (auto-generated, unique)
    private String username;     // login name (unique)
    private String fullName;
    private String passwordHash; // SHA-256 hex, never plain text
    private String createdAt;    // ISO-8601 timestamp

    public User() {}

    public User(String id, String studentId, String username,
                String fullName, String passwordHash, String createdAt) {
        this.id           = id;
        this.studentId    = studentId;
        this.username     = username;
        this.fullName     = fullName;
        this.passwordHash = passwordHash;
        this.createdAt    = createdAt;
    }

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getStudentId()              { return studentId; }
    public void   setStudentId(String sid)    { this.studentId = sid; }

    public String getUsername()               { return username; }
    public void   setUsername(String u)       { this.username = u; }

    public String getFullName()               { return fullName; }
    public void   setFullName(String fn)      { this.fullName = fn; }

    public String getPasswordHash()           { return passwordHash; }
    public void   setPasswordHash(String ph)  { this.passwordHash = ph; }

    public String getCreatedAt()              { return createdAt; }
    public void   setCreatedAt(String ca)     { this.createdAt = ca; }

    @Override
    public String toString() {
        return fullName + " (" + studentId + ")";
    }
}
