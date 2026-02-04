package ape.marketingdepartment.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Logger for publishing operations that stores log entries and notifies listeners.
 * Provides structured logging for API calls with request/response details.
 * Optionally writes API transactions to api.log file for persistence.
 */
public class PublishingLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final List<LogEntry> entries = new ArrayList<>();
    private Consumer<LogEntry> logListener;
    private ApiLogger apiLogger;
    private HolisticLogger holisticLogger;

    /**
     * Set a listener to be notified of new log entries.
     */
    public void setLogListener(Consumer<LogEntry> listener) {
        this.logListener = listener;
    }

    /**
     * Set the API logger for file-based network transaction logging.
     */
    public void setApiLogger(ApiLogger apiLogger) {
        this.apiLogger = apiLogger;
    }

    /**
     * Set the holistic logger for human activity tracking.
     */
    public void setHolisticLogger(HolisticLogger holisticLogger) {
        this.holisticLogger = holisticLogger;
    }

    /**
     * Initialize file-based loggers for a project.
     */
    public void initializeForProject(Path projectDir, boolean apiLoggingEnabled, boolean holisticLoggingEnabled) {
        if (apiLoggingEnabled) {
            this.apiLogger = new ApiLogger(projectDir);
        } else {
            this.apiLogger = null;
        }
        if (holisticLoggingEnabled) {
            this.holisticLogger = new HolisticLogger(projectDir);
        } else {
            this.holisticLogger = null;
        }
    }

    /**
     * Log an info message.
     */
    public void info(String message) {
        addEntry(LogLevel.INFO, message, null);
    }

    /**
     * Log an API request.
     */
    public void logRequest(String service, String method, String url, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(service).append("] ").append(method).append(" ").append(url);
        if (body != null && !body.isBlank()) {
            sb.append("\nRequest Body:\n").append(truncateBody(body, 500));
        }
        addEntry(LogLevel.REQUEST, sb.toString(), null);

        // Also log to API log file
        if (apiLogger != null) {
            apiLogger.logRequest(service, method, url, null, body);
        }
    }

    /**
     * Log an API response.
     */
    public void logResponse(String service, int statusCode, String body, long durationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(service).append("] Response: HTTP ").append(statusCode);
        sb.append(" (").append(durationMs).append("ms)");
        if (body != null && !body.isBlank()) {
            sb.append("\nResponse Body:\n").append(truncateBody(body, 1000));
        }
        LogLevel level = statusCode >= 200 && statusCode < 300 ? LogLevel.RESPONSE : LogLevel.ERROR;
        addEntry(level, sb.toString(), null);

        // Also log to API log file
        if (apiLogger != null) {
            apiLogger.logResponse(service, statusCode, null, body, durationMs);
        }
    }

    /**
     * Log a human action to the holistic log.
     */
    public void logHolisticAction(String action, String details) {
        if (holisticLogger != null) {
            holisticLogger.action(action, details);
        }
    }

    /**
     * Get the holistic logger for direct access.
     */
    public HolisticLogger getHolisticLogger() {
        return holisticLogger;
    }

    /**
     * Log an error.
     */
    public void error(String message, Throwable ex) {
        String details = message;
        if (ex != null) {
            details = message + "\nException: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        addEntry(LogLevel.ERROR, details, ex);
    }

    /**
     * Log a warning.
     */
    public void warn(String message) {
        addEntry(LogLevel.WARN, message, null);
    }

    /**
     * Get all log entries.
     */
    public List<LogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Get entries as formatted text.
     */
    public String getFormattedLog() {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : entries) {
            sb.append(formatEntry(entry)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Clear all log entries.
     */
    public void clear() {
        entries.clear();
    }

    private void addEntry(LogLevel level, String message, Throwable ex) {
        LogEntry entry = new LogEntry(LocalDateTime.now(), level, message, ex);
        entries.add(entry);
        if (logListener != null) {
            logListener.accept(entry);
        }
    }

    private String truncateBody(String body, int maxLength) {
        if (body == null) return "";
        if (body.length() <= maxLength) return body;
        return body.substring(0, maxLength) + "\n... (truncated, " + body.length() + " total chars)";
    }

    private String formatEntry(LogEntry entry) {
        String timestamp = entry.timestamp().format(TIME_FORMAT);
        String prefix = switch (entry.level()) {
            case INFO -> "INFO ";
            case REQUEST -> ">>> ";
            case RESPONSE -> "<<< ";
            case ERROR -> "ERROR";
            case WARN -> "WARN ";
        };
        return "[" + timestamp + "] " + prefix + " " + entry.message();
    }

    public enum LogLevel {
        INFO, REQUEST, RESPONSE, ERROR, WARN
    }

    public record LogEntry(LocalDateTime timestamp, LogLevel level, String message, Throwable exception) {
        public String getFormattedTime() {
            return timestamp.format(TIME_FORMAT);
        }
    }
}
