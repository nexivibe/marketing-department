package ape.marketingdepartment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs human behavior at a holistic level for auditing and analysis.
 * Tracks user actions, decisions, and workflow patterns without logging
 * sensitive content - just the "what" and "when" of user activity.
 */
public class HolisticLogger {

    private static final String LOG_FILE = "holistic.log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logFile;
    private boolean enabled = true;
    private String currentContext = "App";

    public HolisticLogger(Path projectDir) {
        this.logFile = projectDir.resolve(LOG_FILE);
    }

    /**
     * Enable or disable logging.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if logging is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the current context (e.g., "PostEditor", "Pipeline", "Settings").
     */
    public void setContext(String context) {
        this.currentContext = context != null ? context : "App";
    }

    /**
     * Log a user action.
     */
    public void action(String action) {
        log("ACTION", action);
    }

    /**
     * Log a user action with details.
     */
    public void action(String action, String details) {
        log("ACTION", action + (details != null ? " - " + details : ""));
    }

    /**
     * Log when user opens something.
     */
    public void opened(String item) {
        log("OPENED", item);
    }

    /**
     * Log when user closes something.
     */
    public void closed(String item) {
        log("CLOSED", item);
    }

    /**
     * Log when user saves something.
     */
    public void saved(String item) {
        log("SAVED", item);
    }

    /**
     * Log when user creates something.
     */
    public void created(String item) {
        log("CREATED", item);
    }

    /**
     * Log when user deletes something.
     */
    public void deleted(String item) {
        log("DELETED", item);
    }

    /**
     * Log when user edits something.
     */
    public void edited(String item) {
        log("EDITED", item);
    }

    /**
     * Log when user navigates somewhere.
     */
    public void navigated(String destination) {
        log("NAVIGATED", destination);
    }

    /**
     * Log when user triggers a pipeline/workflow.
     */
    public void triggered(String workflow) {
        log("TRIGGERED", workflow);
    }

    /**
     * Log when user makes a decision.
     */
    public void decided(String decision) {
        log("DECIDED", decision);
    }

    /**
     * Log when user reviews something.
     */
    public void reviewed(String item) {
        log("REVIEWED", item);
    }

    /**
     * Log when user publishes something.
     */
    public void published(String item, String destination) {
        log("PUBLISHED", item + " to " + destination);
    }

    /**
     * Log when user exports something.
     */
    public void exported(String item) {
        log("EXPORTED", item);
    }

    /**
     * Log when user configures something.
     */
    public void configured(String item) {
        log("CONFIGURED", item);
    }

    /**
     * Log when a session starts.
     */
    public void sessionStart(String projectName) {
        write("\n" + "=".repeat(80) + "\n");
        log("SESSION", "Started - Project: " + projectName);
    }

    /**
     * Log when a session ends.
     */
    public void sessionEnd() {
        log("SESSION", "Ended");
        write("=".repeat(80) + "\n\n");
    }

    /**
     * Log an error that occurred due to user action.
     */
    public void error(String action, String errorMessage) {
        log("ERROR", action + " failed: " + errorMessage);
    }

    /**
     * Log a milestone or significant event.
     */
    public void milestone(String description) {
        log("MILESTONE", description);
    }

    private void log(String type, String message) {
        if (!enabled) return;

        String entry = String.format("[%s] [%s] %s: %s%n",
                timestamp(), currentContext, type, message);
        write(entry);
    }

    private String timestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    private void write(String content) {
        try {
            Files.writeString(logFile, content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write to holistic log: " + e.getMessage());
        }
    }

    /**
     * Get the path to the log file.
     */
    public Path getLogFile() {
        return logFile;
    }

    /**
     * Clear the log file.
     */
    public void clear() throws IOException {
        if (Files.exists(logFile)) {
            Files.writeString(logFile, "");
        }
    }

    /**
     * Get the log file size in bytes.
     */
    public long getSize() {
        try {
            if (Files.exists(logFile)) {
                return Files.size(logFile);
            }
        } catch (IOException ignored) {
        }
        return 0;
    }

    /**
     * Read recent log entries.
     */
    public String getRecentEntries(int lines) {
        try {
            if (!Files.exists(logFile)) {
                return "";
            }
            var allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            return String.join("\n", allLines.subList(start, allLines.size()));
        } catch (IOException e) {
            return "Error reading log: " + e.getMessage();
        }
    }
}
