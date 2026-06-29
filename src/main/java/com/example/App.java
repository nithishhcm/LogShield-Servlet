package com.example;

// ── Log4j2 imports ────────────────────────────────────────────────────────────
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// ── Standard library imports ──────────────────────────────────────────────────
import java.util.Scanner;

/**
 * App — Interactive Log4j2 Console Demo
 *
 * Usage — type commands at the prompt:
 *
 *   Just text          → logged at INFO
 *   warn: your message → logged at WARN
 *   error: your msg    → logged at ERROR
 *   fatal: your msg    → logged at FATAL
 *   debug: your msg    → logged at DEBUG  (visible because we set Root to DEBUG)
 *   trace: your msg    → logged at TRACE
 *   exit               → logs shutdown message and quits
 *
 * All levels (INFO/WARN/ERROR/FATAL) are saved to logs/app.log via
 * the RollingFileAppender (immediateFlush="true" ensures every line
 * is written to disk instantly).
 *
 * Run with:
 *   mvn compile exec:java
 */
public class App {

    // ── Logger ────────────────────────────────────────────────────────────────
    // Name = "com.example.App" → falls through to Root logger in log4j2.xml
    // Root level is INFO, so DEBUG/TRACE only appear if you lower Root level.
    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {

        logger.info("=== Interactive Log4j2 Demo started ===");
        logger.info("Commands: warn/error/fatal/debug/trace: <message>  |  just text = INFO  |  'exit' to quit");
        logger.warn("WARN  is working — this line will appear on console AND in logs/app.log");
        logger.error("ERROR is working — this line will appear on console AND in logs/app.log");
        logger.fatal("FATAL is working — this line will appear on console AND in logs/app.log");

        try (Scanner scanner = new Scanner(System.in)) {

            // ── State counters for WARN and ERROR escalation ───────────────────
            // consecutiveWarns: resets on any non-WARN input.
            //   Reaching 3 → auto-escalates the 3rd warn to ERROR.
            // totalErrors: never resets — cumulative session error budget.
            //   Reaching 3 → auto-escalates to FATAL and exits the app.
            int consecutiveWarns = 0;
            int totalErrors      = 0;
            boolean shouldExit   = false;

            while (!shouldExit) {

                System.out.print("\n[debug/info/warn/error/fatal]: ");

                if (!scanner.hasNextLine()) {
                    logger.warn("Input stream closed unexpectedly — exiting loop.");
                    break;
                }

                String raw = scanner.nextLine().trim();

                // ── Exit ──────────────────────────────────────────────────────
                if (raw.equalsIgnoreCase("exit")) {
                    logger.warn("Shutdown command received by user. Exiting now.");
                    break;
                }

                // ── Blank input ───────────────────────────────────────────────
                if (raw.isEmpty()) {
                    // DEBUG is below Root's INFO threshold — won't show in file
                    // unless you lower Root level to DEBUG in log4j2.xml
                    System.out.println("  (empty input — type something or 'exit')");
                    continue;
                }

                // ── Level prefix parser ───────────────────────────────────────
                // Accepts:  "warn: something happened"
                //           "warn:something happened"   (space optional)
                //           "WARN: Something"           (case-insensitive)
                // Falls back to INFO if no recognised prefix is found.

                String level;
                String message;

                int colonIdx = raw.indexOf(':');

                if (colonIdx > 0) {
                    // Candidate prefix is the word before the colon
                    String prefix = raw.substring(0, colonIdx).trim().toLowerCase();
                    String rest   = raw.substring(colonIdx + 1).trim();

                    switch (prefix) {
                        case "debug":
                            level   = "DEBUG";
                            message = rest.isEmpty() ? "(no message)" : rest;
                            break;
                        case "trace":
                            level   = "TRACE";
                            message = rest.isEmpty() ? "(no message)" : rest;
                            break;
                        case "warn":
                            level   = "WARN";
                            message = rest.isEmpty() ? "(no message)" : rest;
                            break;
                        case "error":
                            level   = "ERROR";
                            message = rest.isEmpty() ? "(no message)" : rest;
                            break;
                        case "fatal":
                            level   = "FATAL";
                            message = rest.isEmpty() ? "(no message)" : rest;
                            break;
                        default:
                            // Colon was part of the message, not a level prefix
                            level   = "INFO";
                            message = raw;
                    }
                } else {
                    // No colon at all — treat entire input as an INFO message
                    level   = "INFO";
                    message = raw;
                }

                // ── Dispatch to the correct logger method ──────────────────────
                // All of these write to BOTH ConsoleAppender and RollingFileAppender
                // because App uses the Root logger (no dedicated <Logger> block).
                switch (level) {
                    case "TRACE": 
                        logger.trace("User executed action: {}", message); 
                        consecutiveWarns = 0;
                        break;
                    case "DEBUG": 
                        logger.debug("User executed action: {}", message); 
                        consecutiveWarns = 0;
                        break;
                    case "WARN":
                        // ── WARN is REAL now ──────────────────────────────────────
                        // Each WARN increments a consecutive counter.
                        // On the 3rd consecutive WARN (without any other level
                        // in between), the system escalates to ERROR automatically,
                        // simulating a real alerting system that treats repeated
                        // warnings as an unacknowledged problem.
                        consecutiveWarns++;
                        int warnBudget = 3 - consecutiveWarns;
                        if (consecutiveWarns >= 3) {
                            // Simulate a real resource-spike alert being promoted
                            logger.error(
                                "[ESCALATION] WARN→ERROR after {} consecutive warnings. " +
                                "Triggering message: '{}'. System treating repeated warnings as unresolved issue.",
                                consecutiveWarns, message);
                            System.out.println("  ⚠ WARN escalated to ERROR after 3 consecutive warnings. Counter reset.");
                            consecutiveWarns = 0;
                        } else {
                            // Simulate a recoverable resource threshold warning
                            int simulatedUsagePct = 60 + (consecutiveWarns * 15); // 75%, 90%
                            logger.warn(
                                "[THRESHOLD] Resource usage at {}% — message: '{}'. ({} more consecutive warn(s) before auto-escalation to ERROR)",
                                simulatedUsagePct, message, warnBudget);
                            System.out.printf("  ⚡ WARN %d/3 — %d more before auto-escalation to ERROR.%n",
                                consecutiveWarns, warnBudget);
                        }
                        break;

                    case "ERROR":
                        // ── ERROR is REAL now ─────────────────────────────────────
                        // Each ERROR increments a session-wide cumulative counter.
                        // The exception is passed as a real Throwable argument so
                        // Log4j2 prints the full stack trace to the log file —
                        // exactly as it would for a caught runtime exception.
                        // After 3 cumulative errors, the system auto-escalates to
                        // FATAL and terminates, simulating a circuit-breaker.
                        consecutiveWarns = 0; // any ERROR clears the WARN streak
                        totalErrors++;
                        int errorBudget = 3 - totalErrors;
                        RuntimeException simulatedEx = new RuntimeException(
                            "Simulated exception for: " + message);
                        logger.error(
                            "[CIRCUIT-BREAKER] Error #{} recorded — message: '{}'. ({} error(s) remaining before FATAL shutdown)",
                            totalErrors, message, Math.max(0, errorBudget), simulatedEx);
                        System.out.printf("  ❌ ERROR %d/3 — %d error(s) before auto-escalation to FATAL.%n",
                            totalErrors, Math.max(0, errorBudget));
                        if (totalErrors >= 3) {
                            logger.fatal(
                                "[CIRCUIT-BREAKER TRIPPED] FATAL: {} cumulative errors exceeded safety threshold. " +
                                "Initiating emergency shutdown sequence.", totalErrors);
                            System.out.println("  🔴 FATAL: Error limit reached. Emergency shutdown triggered.");
                            shouldExit = true;
                        }
                        break;

                    case "FATAL":
                        logger.fatal("User executed action: {}", message);
                        shouldExit = true;
                        break;
                    default:
                        logger.info("User executed action: {}", message);
                        consecutiveWarns = 0;
                        break;
                }
            }

        } // Scanner auto-closed here

        // ── Explicit Log4j2 shutdown ───────────────────────────────────────────
        // Ensures all appender buffers (especially file appenders) are fully
        // flushed and closed before the JVM exits.
        // Without this, the last few lines written during exec:java may be
        // lost if Maven terminates the JVM before Log4j2's shutdown hook runs.
        logger.info("=== Application shut down cleanly ===");
        org.apache.logging.log4j.LogManager.shutdown();
    }
}
