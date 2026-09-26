package com.prerequix.db;

import com.prerequix.model.User;

/**
 * Repository contract (Interface) for student user account operations.
 *
 * <p>Follows the same Interface → Abstract Class → Concrete Class OOP
 * hierarchy established by {@link CourseRepository}.
 *
 * <p><b>CRUD Mapping:</b>
 * <ul>
 *   <li>CREATE – {@link #register(String, String, String)}</li>
 *   <li>READ   – {@link #findByUsername(String)}, {@link #findById(String)}</li>
 *   <li>UPDATE – {@link #updatePassword(String, String)}</li>
 *   <li>DELETE – {@link #deleteUser(String)}</li>
 * </ul>
 */
public interface UserRepository {

    /**
     * Registers a new student, hashes the password, assigns a student ID,
     * and persists the record. (CREATE)
     *
     * @param fullName display name
     * @param username login name (must be unique)
     * @param password plain-text password (will be hashed internally)
     * @return the newly created {@link User}
     * @throws IllegalArgumentException if username is already taken
     */
    User register(String fullName, String username, String password) throws Exception;

    /**
     * Validates credentials and returns the matching user. (READ / authenticate)
     *
     * @param username login name
     * @param password plain-text password to verify against stored hash
     * @return the authenticated {@link User}
     * @throws IllegalArgumentException if credentials are invalid
     */
    User login(String username, String password) throws Exception;

    /** Returns the user with the given username, or {@code null} if not found. (READ) */
    User findByUsername(String username) throws Exception;

    /** Returns the user with the given UUID, or {@code null} if not found. (READ) */
    User findById(String id) throws Exception;

    /** Returns {@code true} if the username is already registered. */
    boolean usernameExists(String username) throws Exception;

    /** Changes the stored password hash for the given user ID. (UPDATE) */
    void updatePassword(String userId, String newPassword) throws Exception;

    /** Permanently removes the account. (DELETE) */
    void deleteUser(String userId) throws Exception;
}
