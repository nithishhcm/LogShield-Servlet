package com.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * LoginServlet — Handles POST /api/login
 *
 * Reads a JSON body: { "username": "...", "password": "..." }
 * Delegates to UserService.login() and returns a JSON response.
 *
 * Log4j2 usage (via UserService — all events go to logs/auth.log):
 *   INFO  → successful authentication
 *   WARN  → wrong password or username not found
 *   ERROR → database failure
 */
public class LoginServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(LoginServlet.class);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // CORS headers
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json;charset=UTF-8");

        // ── Read JSON request body ─────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        JSONObject json;
        String username, password;

        try {
            json     = new JSONObject(sb.toString());
            username = json.optString("username", "").trim();
            password = json.optString("password", "").trim();
        } catch (Exception e) {
            logger.warn("[LOGIN-SERVLET] Malformed JSON body received from {}",
                        req.getRemoteAddr());
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "error", "Invalid JSON request body.");
            return;
        }

        // ── Delegate to UserService ────────────────────────────────────────────
        UserService.LoginResult result = UserService.login(username, password);

        // ── Build HTTP response ────────────────────────────────────────────────
        switch (result) {
            case SUCCESS:
                sendJson(resp, HttpServletResponse.SC_OK,
                        "message", "Login successful! Welcome back, " + username + "!");
                break;

            case WRONG_PASSWORD:
            case USER_NOT_FOUND:
                // Return the same message for both to avoid username enumeration
                sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                        "error", "Invalid username or password. Please try again.");
                break;

            case DB_ERROR:
            default:
                sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "error", "A database error occurred. Please try again later.");
                break;
        }
    }

    /** Handles browser pre-flight CORS OPTIONS requests. */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin",  "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private void sendJson(HttpServletResponse resp, int status, String key, String value)
            throws IOException {
        resp.setStatus(status);
        try (PrintWriter writer = resp.getWriter()) {
            writer.print(new JSONObject().put(key, value));
        }
    }
}
