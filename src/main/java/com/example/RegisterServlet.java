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
 * RegisterServlet — Handles POST /api/register
 *
 * Reads a JSON body: { "username": "...", "email": "...", "password": "..." }
 * Delegates to UserService.register() and returns a JSON response.
 *
 * Log4j2 usage (via UserService — all events go to logs/auth.log):
 *   INFO  → registration successful
 *   WARN  → duplicate username or blank fields
 *   ERROR → database failure
 */
public class RegisterServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(RegisterServlet.class);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // Set CORS headers so the HTML page can call this API
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json;charset=UTF-8");

        // ── Read JSON request body ─────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        JSONObject json;
        String username, email, password;

        try {
            json     = new JSONObject(sb.toString());
            username = json.optString("username", "").trim();
            email    = json.optString("email",    "").trim();
            password = json.optString("password", "").trim();
        } catch (Exception e) {
            logger.warn("[REGISTER-SERVLET] Malformed JSON body received from {}",
                        req.getRemoteAddr());
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "error", "Invalid JSON request body.");
            return;
        }

        // ── Delegate to UserService ────────────────────────────────────────────
        UserService.RegisterResult result = UserService.register(username, email, password);

        // ── Build HTTP response based on result ───────────────────────────────
        switch (result) {
            case SUCCESS:
                sendJson(resp, HttpServletResponse.SC_CREATED,
                        "message", "Registration successful! You can now log in.");
                break;

            case DUPLICATE_USERNAME:
                sendJson(resp, HttpServletResponse.SC_CONFLICT,
                        "error", "Username '" + username + "' is already taken. Please choose another.");
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
