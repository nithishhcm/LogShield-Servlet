package com.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UserService — Authentication Business Logic
 *
 * Every action (register / login) performed from ANYWHERE — the browser,
 * the console, or any future client — is:
 *
 *   1. Printed LIVE to the terminal in a colour-coded styled event block
 *      so the operator can see what is happening in real time.
 *
 *   2. Written to logs/auth.log via Log4j2 at the correct level:
 *        INFO  — happy path: registered / logged in successfully
 *        WARN  — problem: duplicate username, wrong password, blank fields
 *        ERROR — unexpected DB failure (full stack trace)
 *
 * Log4j2 logger name = "com.example.UserService" → mapped to AuthFileAppender
 * with additivity=false so auth events never appear in app.log.
 */
public class UserService {

    private static final Logger logger = LogManager.getLogger(UserService.class);

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ANSI colour codes
    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String CYAN   = "\033[1;36m";
    private static final String GREEN  = "\033[1;32m";
    private static final String YELLOW = "\033[1;33m";
    private static final String RED    = "\033[1;31m";
    private static final String BRED   = "\033[1;91m";  // bright red for FATAL
    private static final String GREY   = "\033[0;37m";
    private static final String DIM    = "\033[2m";

    // Result codes
    public enum RegisterResult { SUCCESS, DUPLICATE_USERNAME, DB_ERROR }
    public enum LoginResult    { SUCCESS, WRONG_PASSWORD, USER_NOT_FOUND, DB_ERROR }

    // ══════════════════════════════════════════════════════════════════════════
    //  REGISTER
    // ══════════════════════════════════════════════════════════════════════════

    public static RegisterResult register(String username, String email, String password) {

        String source = detectSource(); // "WEB" or "CONSOLE"
        String ts     = LocalDateTime.now().format(TS);

        // ── Blank field guard ─────────────────────────────────────────────────
        if (username == null || username.isBlank() ||
            email    == null || email.isBlank()    ||
            password == null || password.isBlank()) {

            printEvent(YELLOW, "WARN", "REGISTER", source, ts,
                    "Blank field(s) in registration request.",
                    "username=" + username + " | email=" + email);

            logger.warn("[REGISTER] Blank field(s) — username='{}', email='{}', passwordProvided={}",
                        username, email, (password != null && !password.isBlank()));
            return RegisterResult.DUPLICATE_USERNAME;
        }

        // ── Announce the attempt ─────────────────────────────────────────────
        printEvent(CYAN, "INFO", "REGISTER", source, ts,
                "New registration attempt.",
                "username=" + username + " | email=" + email);
        logger.info("[REGISTER] Attempting — username='{}', email='{}'", username, email);

        try {
            boolean inserted = DatabaseManager.insertUser(username, email, password);

            if (inserted) {
                // ── INFO: success ─────────────────────────────────────────────
                printEvent(GREEN, "INFO", "REGISTER", source, ts,
                        "SUCCESS \u2714  User registered and saved to database.",
                        "username=" + username + " | email=" + email);
                logger.info("[REGISTER] SUCCESS — User '{}' registered.", username);
                return RegisterResult.SUCCESS;

            } else {
                // ── WARN: duplicate ───────────────────────────────────────────
                printEvent(YELLOW, "WARN", "REGISTER", source, ts,
                        "DUPLICATE USERNAME \u26a0  Registration rejected.",
                        "username='" + username + "' already exists — possible repeated attempt.");
                logger.warn("[REGISTER] DUPLICATE — username='{}' already exists.", username);
                return RegisterResult.DUPLICATE_USERNAME;
            }

        } catch (SQLException e) {
            // ── ERROR: DB failure ─────────────────────────────────────────────
            printEvent(RED, "ERROR", "REGISTER", source, ts,
                    "DATABASE ERROR \u2716  Exception during registration.",
                    "username=" + username + " | exception=" + e.getMessage()
                    + " | see logs/errors.log for stack trace");
            logger.error("[REGISTER] ERROR — DB exception for username='{}'.", username, e);
            return RegisterResult.DB_ERROR;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOGIN
    // ══════════════════════════════════════════════════════════════════════════

    public static LoginResult login(String username, String password) {

        String source = detectSource();
        String ts     = LocalDateTime.now().format(TS);

        // ── Blank field guard ─────────────────────────────────────────────────
        if (username == null || username.isBlank() ||
            password == null || password.isBlank()) {

            printEvent(YELLOW, "WARN", "LOGIN", source, ts,
                    "Blank credentials supplied.",
                    "username=" + username + " | passwordProvided="
                    + (password != null && !password.isBlank()));

            logger.warn("[LOGIN] Blank credentials — username='{}', passwordProvided={}",
                        username, (password != null && !password.isBlank()));
            return LoginResult.USER_NOT_FOUND;
        }

        // ── Announce the attempt ─────────────────────────────────────────────
        printEvent(CYAN, "INFO", "LOGIN", source, ts,
                "Login attempt received.",
                "username=" + username);
        logger.info("[LOGIN] Attempt — username='{}'", username);

        try {
            boolean valid = DatabaseManager.validateUser(username, password);

            if (valid) {
                // ── INFO: success ─────────────────────────────────────────────
                printEvent(GREEN, "INFO", "LOGIN", source, ts,
                        "SUCCESS \u2714  User authenticated. Session established.",
                        "username=" + username);
                logger.info("[LOGIN] SUCCESS — '{}' authenticated.", username);
                return LoginResult.SUCCESS;

            } else {
                // ── WARN: bad credentials ─────────────────────────────────────
                printEvent(YELLOW, "WARN", "LOGIN", source, ts,
                        "FAILED \u26a0  Invalid username or password.",
                        "username='" + username + "' — wrong password or user not found."
                        + " Repeated failures may indicate a brute-force attempt.");
                logger.warn("[LOGIN] FAILED — bad credentials for username='{}'.", username);
                return LoginResult.WRONG_PASSWORD;
            }

        } catch (SQLException e) {
            // ── ERROR: DB failure ─────────────────────────────────────────────
            printEvent(RED, "ERROR", "LOGIN", source, ts,
                    "DATABASE ERROR \u2716  Exception during authentication.",
                    "username=" + username + " | exception=" + e.getMessage()
                    + " | see logs/errors.log for stack trace");
            logger.error("[LOGIN] ERROR — DB exception for username='{}'.", username, e);
            return LoginResult.DB_ERROR;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Detects whether this call came from a Jetty web thread (browser request)
     * or from the main/console thread. Used to label the source in the output.
     */
    private static String detectSource() {
        String threadName = Thread.currentThread().getName();
        if (threadName.startsWith("qtp") || threadName.contains("jetty")) {
            return "WEB";
        }
        return "CONSOLE";
    }

    /**
     * Prints a fully styled, coloured event block to System.out.
     * This makes every action visible in the terminal immediately,
     * whether it came from the browser or a console command.
     *
     * Example output:
     *
     *   ┌─────────────────────────────────────────────────────┐
     *   │  11:24:36  INFO   REGISTER  [WEB]                   │
     *   │  SUCCESS ✔  User registered and saved to database.  │
     *   │  username=alice | email=alice@mail.com              │
     *   └─────────────────────────────────────────────────────┘
     */
    private static void printEvent(String color, String level, String action,
                                   String source, String ts,
                                   String headline, String detail) {

        // Source badge colour
        String srcColor = "WEB".equals(source) ? "\033[1;35m" : "\033[1;34m"; // magenta=WEB, blue=CONSOLE

        String line = "─".repeat(57);

        System.out.println();
        System.out.println("  " + color + "┌" + line + "┐" + RESET);
        System.out.printf( "  %s│%s  %s%s%s  %s%-6s%s  %s%-8s%s  %s[%-7s]%s %s│%n",
                color, RESET,
                DIM, ts, RESET,
                color + BOLD, level, RESET,
                BOLD, action, RESET,
                srcColor, source, RESET,
                color);
        System.out.println("  " + color + "├" + line + "┤" + RESET);
        System.out.printf( "  %s│%s  %-55s%s│%n", color, RESET, headline, color);

        // Wrap detail at 55 chars
        String[] words = detail.split(" \\| ");
        for (String word : words) {
            System.out.printf("  %s│%s  %s%-55s%s%s│%n", color, GREY, DIM, word, RESET, color);
        }

        System.out.println("  " + color + "└" + line + "┘" + RESET);
        System.out.println();
    }
}
