package com.example;

// ── Log4j2 imports ────────────────────────────────────────────────────────────
// LogManager  → factory that creates / retrieves Logger instances by name
import org.apache.logging.log4j.LogManager;

// Logger      → the object you call .debug(), .info(), .warn(), .error() on
import org.apache.logging.log4j.Logger;

/**
 * EventService — Custom Event Isolation Demo
 *
 * This class demonstrates Log4j2 "custom event isolation":
 *
 *   • The logger name MUST exactly match the <Logger name="..."> value in
 *     log4j2.xml.  Here that is "com.example.EventService".
 *
 *   • Because additivity="false" is set in log4j2.xml for this logger,
 *     events logged here are routed ONLY to:
 *       1. ConsoleAppender   → your terminal (shared visual output)
 *       2. IsolatedFileAppender → logs/isolated-events.log (exclusive file)
 *
 *   • They do NOT propagate to the Root logger, so they will NOT appear
 *     in logs/app.log — zero duplicate entries.
 *
 * To see the isolation in action run:
 *   mvn compile exec:java
 * then inspect both files:
 *   logs/app.log            ← NO EventService entries here
 *   logs/isolated-events.log ← ONLY EventService entries here
 */
public class EventService {

    // ── Logger instantiation ──────────────────────────────────────────────────
    //
    // LogManager.getLogger(EventService.class) resolves to the string name
    // "com.example.EventService" — the SAME string used as name="..." in the
    // <Logger> block inside log4j2.xml.  This is how Log4j2 matches at runtime.
    //
    // ⚠ BEGINNER MISTAKE: using a hard-coded string like
    //     LogManager.getLogger("eventService")   // ← wrong case / name
    //   The name won't match the XML entry and the event falls through to Root,
    //   breaking isolation.  Always pass YourClassName.class.
    //
    private static final Logger logger = LogManager.getLogger(EventService.class);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Publishes a named domain event and logs it at the appropriate severity.
     *
     * @param eventType  Short name of the event, e.g. "ORDER_PLACED"
     * @param payload    Serialised event payload (JSON string, ID, etc.)
     */
    public void publishEvent(String eventType, String payload) {

        // DEBUG — internal trace; captured because this logger is set to DEBUG
        // in log4j2.xml (root is INFO, but our dedicated logger overrides that).
        logger.debug("Publishing event — type='{}', payload='{}'", eventType, payload);

        // INFO — normal, expected domain milestone
        logger.info("Event published successfully — type='{}', payload='{}'", eventType, payload);
    }

    /**
     * Handles a received domain event and logs the outcome.
     *
     * @param eventType  Short name of the event
     * @param source     Origin service or component
     */
    public void handleEvent(String eventType, String source) {

        logger.debug("Received event — type='{}', source='{}'", eventType, source);

        try {
            // Simulate processing — replace with real logic
            processEvent(eventType, source);
            logger.info("Event handled — type='{}', source='{}'", eventType, source);

        } catch (UnsupportedOperationException e) {
            // WARN — recoverable; event type unknown but app continues
            logger.warn("Unrecognised event type '{}' from '{}' — skipping.", eventType, source, e);

        } catch (Exception e) {
            // ERROR — two-argument overload: message + throwable → full stack trace
            // ⚠ BEGINNER MISTAKE: logger.error("msg: " + e.getMessage()) loses stack trace.
            //   Always pass the exception as a second argument.
            logger.error("Failed to handle event type='{}' from source='{}'.", eventType, source, e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Stub processor — extend with real event handling logic.
     */
    private void processEvent(String eventType, String source) {
        switch (eventType) {
            case "ORDER_PLACED":
                // e.g. trigger inventory reservation
                break;
            case "PAYMENT_CONFIRMED":
                // e.g. trigger fulfilment workflow
                break;
            default:
                throw new UnsupportedOperationException(
                        "No handler registered for event type: " + eventType);
        }
    }

    // ── Entry point for quick standalone testing ──────────────────────────────
    //
    // You can also call EventService from App.main() instead.
    //
    public static void main(String[] args) {

        EventService svc = new EventService();

        System.out.println("=== EventService isolation demo ===\n");

        // ① Happy-path: known event types → INFO + DEBUG in isolated-events.log
        svc.publishEvent("ORDER_PLACED",      "{\"orderId\":\"ORD-001\",\"amount\":99.99}");
        svc.publishEvent("PAYMENT_CONFIRMED", "{\"orderId\":\"ORD-001\",\"txnId\":\"TXN-XYZ\"}");

        // ② Inbound event handling
        svc.handleEvent("ORDER_PLACED",      "checkout-service");
        svc.handleEvent("PAYMENT_CONFIRMED", "payment-service");

        // ③ Unknown event type → WARN logged with stack trace in isolated-events.log
        svc.handleEvent("SHIPMENT_DISPATCHED", "logistics-service");

        System.out.println("\n=== Done — check logs/isolated-events.log ===");
    }
}
