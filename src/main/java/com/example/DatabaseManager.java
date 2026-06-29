package com.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseManager — SQLite Database Layer
 *
 * Manages the SQLite connection pool (single-connection for simplicity) and
 * provides all raw SQL operations used by UserService.
 *
 * SQLite creates the .db file automatically on first connection, so no manual
 * database setup is required — just run the app and the file appears.
 *
 * Log4j2 usage:
 *   INFO  — successful DB init, table created
 *   ERROR — SQL exceptions during queries (logged with full stack trace)
 *   FATAL — cannot open the database file at startup
 */
public class DatabaseManager {

    // This logger name falls through to Root → goes to app.log + console
    private static final Logger logger = LogManager.getLogger(DatabaseManager.class);

    // SQLite database file location — created relative to the working directory
    private static final String DB_URL = "jdbc:sqlite:logs/users.db";

    // Shared connection (sufficient for a single-user demo)
    private static Connection connection;

    // ── Private constructor: utility class, no instances ──────────────────────
    private DatabaseManager() {}

    /**
     * Initialises the database:
     *  1. Opens (or creates) the SQLite file at logs/users.db
     *  2. Creates the 'users' table if it does not already exist
     *
     * Called once from WebApp.main() before the server starts.
     * Throws SQLException if anything goes wrong so WebApp can log FATAL and exit.
     */
    public static void init() throws SQLException {
        logger.info("Initialising SQLite database at: {}", DB_URL);

        try {
            // Ensure the SQLite JDBC driver is registered (required for older JDKs)
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on classpath.", e);
        }

        // Open the connection — creates the .db file if it doesn't exist
        connection = DriverManager.getConnection(DB_URL);
        logger.info("Database connection established successfully.");

        // Create the users table if this is the first run
        createUsersTable();
    }

    /**
     * Creates the 'users' table with:
     *   id       — auto-incrementing primary key
     *   username — must be unique; used for login
     *   email    — stored for registration records
     *   password — plain text for demo purposes
     *              ⚠ In production, use BCrypt / Argon2 password hashing!
     */
    private static void createUsersTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id       INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT    NOT NULL UNIQUE, " +
                "email    TEXT    NOT NULL, " +
                "password TEXT    NOT NULL" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            logger.info("Table 'users' is ready (created or already exists).");
        }
    }

    /**
     * Inserts a new user record.
     *
     * @return true  if the insert succeeded
     *         false if the username already exists (UNIQUE constraint violation)
     * @throws SQLException for any other unexpected DB error
     */
    public static boolean insertUser(String username, String email, String password)
            throws SQLException {

        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            // SQLite error code 19 = SQLITE_CONSTRAINT (UNIQUE violation)
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                return false; // caller will log WARN
            }
            throw e; // re-throw unexpected errors; caller will log ERROR
        }
    }

    /**
     * Looks up a user by username and validates the password.
     *
     * @return true  if a matching username+password row is found
     *         false if username not found or password mismatch
     * @throws SQLException for any DB error
     */
    public static boolean validateUser(String username, String password)
            throws SQLException {

        String sql = "SELECT password FROM users WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    return storedPassword.equals(password);
                }
                // Username not found
                return false;
            }
        }
    }

    /**
     * Returns the shared connection (used for health checks if needed).
     */
    public static Connection getConnection() {
        return connection;
    }
}
