package com.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

/**
 * WebApp — Embedded Jetty Entry Point + Interactive Console
 *
 * Starts the web server on port 8080 AND launches a console thread.
 * You can type commands in the same terminal window while the server runs.
 *
 * Console commands:
 *   register <username> <email> <password>  → registers a new user
 *   login    <username> <password>           → authenticates a user
 *   list                                     → shows all registered users
 *   logs                                     → prints the last 10 auth log events
 *   help                                     → shows this command list
 *   exit                                     → stops the server and exits
 *
 * Run with:  mvn compile exec:java
 * Then open: http://localhost:8080
 */
public class WebApp {

    private static final Logger logger = LogManager.getLogger(WebApp.class);

    // Shared flag so the console thread can stop the server
    private static volatile Server server;

    public static void main(String[] args) {

        printBanner();
        logger.info("=== Log4j2 Web Application starting ===");

        // ── Step 1: Initialise the database ───────────────────────────────────
        try {
            DatabaseManager.init();
        } catch (Exception e) {
            logger.fatal("FATAL: Database initialisation failed. Application cannot start.", e);
            printFatal("Database initialisation failed!",
                    "The application cannot start.",
                    "Cause: " + e.getMessage(),
                    "Check logs/errors.log for the full stack trace.");
            LogManager.shutdown();
            System.exit(1);
        }

        // ── Step 2: Create and configure the Jetty server ────────────────────
        server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Register API servlets
        context.addServlet(new ServletHolder(new RegisterServlet()), "/api/register");
        context.addServlet(new ServletHolder(new LoginServlet()),    "/api/login");

        // Serve static files (index.html) from webapp resource directory
        String webappPath = WebApp.class.getClassLoader()
                .getResource("webapp").toExternalForm();
        context.setResourceBase(webappPath);
        ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(defaultHolder, "/");

        server.setHandler(context);

        // ── Step 3: Start Jetty in a background thread ───────────────────────
        // We start Jetty in its own thread so the MAIN thread can run the console.
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
                logger.info("Server started successfully on http://localhost:8080");
                server.join();
            } catch (Exception e) {
                logger.error("Server error: {}", e.getMessage(), e);
            }
        }, "jetty-server");
        serverThread.setDaemon(true); // dies automatically when main thread exits
        serverThread.start();

        // Give Jetty a moment to bind the port before printing the prompt
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        printServerReady();

        // ── Step 4: Run the interactive console on the main thread ────────────
        runConsole();

        // ── Step 5: Shutdown ──────────────────────────────────────────────────
        logger.info("=== Log4j2 Web Application stopped ===");
        LogManager.shutdown();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INTERACTIVE CONSOLE
    // ══════════════════════════════════════════════════════════════════════════

    private static void runConsole() {

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n\033[1;36m[console]\033[0m > ");

                if (!sc.hasNextLine()) break;
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {

                    // ── register <username> <email> <password> ────────────────
                    case "register": {
                        if (parts.length < 4) {
                            consoleWarn("Usage: register <username> <email> <password>");
                            break;
                        }
                        String username = parts[1];
                        String email    = parts[2];
                        String password = parts[3];

                        System.out.printf("  Registering user '%s'...%n", username);
                        UserService.RegisterResult result =
                                UserService.register(username, email, password);

                        switch (result) {
                            case SUCCESS:
                                consoleInfo("SUCCESS — User '" + username +
                                        "' registered. Check logs/auth.log [INFO].");
                                break;
                            case DUPLICATE_USERNAME:
                                consoleWarn("WARN — Username '" + username +
                                        "' already exists. Check logs/auth.log [WARN].");
                                break;
                            case DB_ERROR:
                                consoleError("ERROR — Database error. Check logs/errors.log [ERROR].");
                                break;
                        }
                        break;
                    }

                    // ── login <username> <password> ───────────────────────────
                    case "login": {
                        if (parts.length < 3) {
                            consoleWarn("Usage: login <username> <password>");
                            break;
                        }
                        String username = parts[1];
                        String password = parts[2];

                        System.out.printf("  Authenticating '%s'...%n", username);
                        UserService.LoginResult result =
                                UserService.login(username, password);

                        switch (result) {
                            case SUCCESS:
                                consoleInfo("SUCCESS — User '" + username +
                                        "' authenticated. Check logs/auth.log [INFO].");
                                break;
                            case WRONG_PASSWORD:
                            case USER_NOT_FOUND:
                                consoleWarn("WARN — Invalid credentials for '" + username +
                                        "'. Check logs/auth.log [WARN].");
                                break;
                            case DB_ERROR:
                                consoleError("ERROR — Database error. Check logs/errors.log [ERROR].");
                                break;
                        }
                        break;
                    }

                    // ── list ──────────────────────────────────────────────────
                    case "list": {
                        listUsers();
                        break;
                    }

                    // ── logs ──────────────────────────────────────────────────
                    case "logs": {
                        showRecentLogs(parts.length > 1 ? parts[1] : "auth");
                        break;
                    }

                    // ── help ──────────────────────────────────────────────────
                    case "help": {
                        printHelp();
                        break;
                    }

                    // ── exit ──────────────────────────────────────────────────
                    case "exit": {
                        logger.info("Console: exit command received. Stopping server.");
                        System.out.println("\n  Stopping server...");
                        try { server.stop(); } catch (Exception ignored) {}
                        System.out.println("  Goodbye!\n");
                        return;
                    }

                    default:
                        consoleWarn("Unknown command '" + cmd + "'. Type 'help' to see available commands.");
                }
            }
        }
    }

    // ── List all users from the DB ─────────────────────────────────────────
    private static void listUsers() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │                    REGISTERED USERS                     │");
        System.out.println("  ├────┬──────────────────────┬────────────────────────────┤");
        System.out.printf( "  │ %-2s │ %-20s │ %-26s │%n", "ID", "Username", "Email");
        System.out.println("  ├────┼──────────────────────┼────────────────────────────┤");

        int count = 0;
        try {
            Connection conn = DatabaseManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs   = stmt.executeQuery(
                         "SELECT id, username, email FROM users ORDER BY id")) {

                while (rs.next()) {
                    System.out.printf("  │ %-2d │ %-20s │ %-26s │%n",
                            rs.getInt("id"),
                            truncate(rs.getString("username"), 20),
                            truncate(rs.getString("email"),    26));
                    count++;
                }
            }
        } catch (Exception e) {
            consoleError("Could not query the database: " + e.getMessage());
            logger.error("[CONSOLE] Failed to list users.", e);
        }

        System.out.println("  └────┴──────────────────────┴────────────────────────────┘");
        System.out.printf( "  %d user(s) found.%n", count);
        logger.info("[CONSOLE] Listed {} user(s) from the database.", count);
    }

    // ── Show recent lines from a log file ─────────────────────────────────
    private static void showRecentLogs(String logType) {
        String fileName = logType.equals("app")    ? "logs/app.log"
                        : logType.equals("errors") ? "logs/errors.log"
                        : "logs/auth.log";

        System.out.println("\n  ── Last entries from " + fileName + " ──\n");

        java.io.File logFile = new java.io.File(fileName);
        if (!logFile.exists()) {
            consoleWarn("Log file not found: " + fileName);
            return;
        }

        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(logFile.toPath());
            int start = Math.max(0, lines.size() - 20);
            for (int i = start; i < lines.size(); i++) {
                String l = lines.get(i);
                // Colour-code by level
                if      (l.contains(" FATAL ")) System.out.println("  \033[1;31m" + l + "\033[0m");
                else if (l.contains(" ERROR ")) System.out.println("  \033[0;31m" + l + "\033[0m");
                else if (l.contains(" WARN  ")) System.out.println("  \033[0;33m" + l + "\033[0m");
                else if (l.contains(" INFO  ")) System.out.println("  \033[0;36m" + l + "\033[0m");
                else                            System.out.println("  " + l);
            }
        } catch (Exception e) {
            consoleError("Could not read log file: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI helpers
    // ══════════════════════════════════════════════════════════════════════════

    private static void consoleInfo(String msg) {
        System.out.println("  \033[0;36m✔ " + msg + "\033[0m");
    }
    private static void consoleWarn(String msg) {
        System.out.println("  \033[0;33m⚠ " + msg + "\033[0m");
    }
    private static void consoleError(String msg) {
        System.out.println("  \033[0;31m✖ " + msg + "\033[0m");
    }
    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  \033[1;36m╔══════════════════════════════════════════════════════════════╗\033[0m");
        System.out.println("  \033[1;36m║       SecureAuth — Log4j2 Web Application                    ║\033[0m");
        System.out.println("  \033[1;36m║       All browser & console actions print here in real-time  ║\033[0m");
        System.out.println("  \033[1;36m╚══════════════════════════════════════════════════════════════╝\033[0m");
        System.out.println();
    }

    private static void printServerReady() {
        String line = "─".repeat(57);
        System.out.println();
        System.out.println("  \033[1;32m┌" + line + "┐\033[0m");
        System.out.println("  \033[1;32m│  SERVER STARTED SUCCESSFULLY                            │\033[0m");
        System.out.println("  \033[1;32m├" + line + "┤\033[0m");
        System.out.printf( "  \033[1;32m│\033[0m  Browser UI  → \033[1;36mhttp://localhost:8080\033[0m%29s\033[1;32m│\033[0m%n", "");
        System.out.printf( "  \033[1;32m│\033[0m  Log files  → \033[0;37mlogs/auth.log, app.log, errors.log\033[0m%9s\033[1;32m│\033[0m%n", "");
        System.out.printf( "  \033[1;32m│\033[0m  Console    → type \033[1;33mhelp\033[0m for all commands%22s\033[1;32m│\033[0m%n", "");
        System.out.printf( "  \033[1;32m│\033[0m  \033[2mEvery browser & console action prints live below\033[0m%9s\033[1;32m│\033[0m%n", "");
        System.out.println("  \033[1;32m└" + line + "┘\033[0m");
        System.out.println();
    }

    /** Prints a FATAL styled block to the terminal for critical startup failures. */
    private static void printFatal(String... lines) {
        String border = "═".repeat(57);
        System.out.println();
        System.out.println("  \033[1;91m╔" + border + "╗\033[0m");
        System.out.println("  \033[1;91m║  FATAL ERROR — APPLICATION CANNOT START               ║\033[0m");
        System.out.println("  \033[1;91m╠" + border + "╣\033[0m");
        for (String l : lines) {
            System.out.printf("  \033[1;91m║\033[0m  \033[0;31m%-55s\033[0m\033[1;91m║\033[0m%n", l);
        }
        System.out.println("  \033[1;91m╚" + border + "╝\033[0m");
        System.out.println();
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("  \033[1;37m┌─────────────────────────────────────────────────────────────────┐\033[0m");
        System.out.println("  \033[1;37m│                   CONSOLE COMMANDS                              │\033[0m");
        System.out.println("  \033[1;37m├─────────────────────────────────────────────────────────────────┤\033[0m");
        System.out.println("  \033[0;36m│  register <username> <email> <password>                         │\033[0m");
        System.out.println("  \033[0;37m│    → Register a new user   [logs INFO or WARN to auth.log]      │\033[0m");
        System.out.println("  \033[0;36m│  login <username> <password>                                    │\033[0m");
        System.out.println("  \033[0;37m│    → Log in a user         [logs INFO or WARN to auth.log]      │\033[0m");
        System.out.println("  \033[0;36m│  list                                                           │\033[0m");
        System.out.println("  \033[0;37m│    → Show all registered users in a table                       │\033[0m");
        System.out.println("  \033[0;36m│  logs [auth|app|errors]                                         │\033[0m");
        System.out.println("  \033[0;37m│    → Print last 20 lines of a log file (default: auth)          │\033[0m");
        System.out.println("  \033[0;36m│  help                                                           │\033[0m");
        System.out.println("  \033[0;37m│    → Show this help screen                                      │\033[0m");
        System.out.println("  \033[0;36m│  exit                                                           │\033[0m");
        System.out.println("  \033[0;37m│    → Stop the server and quit                                   │\033[0m");
        System.out.println("  \033[1;37m└─────────────────────────────────────────────────────────────────┘\033[0m");
        System.out.println();
        System.out.println("  \033[0;37mExamples:\033[0m");
        System.out.println("  \033[0;32m  register alice alice@mail.com secret123\033[0m");
        System.out.println("  \033[0;32m  login alice secret123\033[0m");
        System.out.println("  \033[0;32m  login alice wrongpass       \033[0;33m← triggers WARN in auth.log\033[0m");
        System.out.println("  \033[0;32m  register alice x@x.com p    \033[0;33m← triggers WARN (duplicate)\033[0m");
        System.out.println("  \033[0;32m  logs\033[0m");
        System.out.println("  \033[0;32m  logs errors\033[0m");
        System.out.println("  \033[0;32m  list\033[0m");
        System.out.println();
    }
}
