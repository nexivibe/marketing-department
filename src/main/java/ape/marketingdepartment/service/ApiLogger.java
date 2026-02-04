package ape.marketingdepartment.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs all network/API transactions to an api.log file in the project directory.
 * This captures every HTTP request and response for debugging and auditing.
 */
public class ApiLogger {

    private static final String LOG_FILE = "api.log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path logFile;
    private boolean enabled = true;

    public ApiLogger(Path projectDir) {
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
     * Log an outgoing HTTP request.
     */
    public void logRequest(String service, String method, String url, String headers, String body) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(100)).append("\n");
        sb.append("[").append(timestamp()).append("] >>> REQUEST to ").append(service).append("\n");
        sb.append("Method: ").append(method).append("\n");
        sb.append("URL: ").append(url).append("\n");

        if (headers != null && !headers.isBlank()) {
            sb.append("-".repeat(40)).append(" HEADERS ").append("-".repeat(51)).append("\n");
            sb.append(sanitizeHeaders(headers)).append("\n");
        }

        if (body != null && !body.isBlank()) {
            sb.append("-".repeat(40)).append(" REQUEST BODY ").append("-".repeat(46)).append("\n");
            sb.append(body).append("\n");
        }
        sb.append("-".repeat(100)).append("\n");

        write(sb.toString());
    }

    /**
     * Log an incoming HTTP response.
     */
    public void logResponse(String service, int statusCode, String headers, String body, long durationMs) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] <<< RESPONSE from ").append(service).append("\n");
        sb.append("Status: ").append(statusCode).append(" (").append(durationMs).append("ms)\n");

        if (headers != null && !headers.isBlank()) {
            sb.append("-".repeat(40)).append(" HEADERS ").append("-".repeat(51)).append("\n");
            sb.append(headers).append("\n");
        }

        if (body != null && !body.isBlank()) {
            sb.append("-".repeat(40)).append(" RESPONSE BODY ").append("-".repeat(45)).append("\n");
            sb.append(truncate(body, 10000)).append("\n");
        }
        sb.append("=".repeat(100)).append("\n\n");

        write(sb.toString());
    }

    /**
     * Log a simplified request (without full headers).
     */
    public void logSimpleRequest(String service, String method, String url) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] >>> ").append(service);
        sb.append(" ").append(method).append(" ").append(url).append("\n");

        write(sb.toString());
    }

    /**
     * Log a simplified response.
     */
    public void logSimpleResponse(String service, int statusCode, long durationMs) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] <<< ").append(service);
        sb.append(" HTTP ").append(statusCode).append(" (").append(durationMs).append("ms)\n");

        write(sb.toString());
    }

    /**
     * Log an error during API communication.
     */
    public void logError(String service, String message, Throwable exception) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] !!! ERROR in ").append(service).append("\n");
        sb.append("Message: ").append(message).append("\n");
        if (exception != null) {
            sb.append("Exception: ").append(exception.getClass().getName());
            sb.append(": ").append(exception.getMessage()).append("\n");
        }
        sb.append("=".repeat(100)).append("\n\n");

        write(sb.toString());
    }

    /**
     * Sanitize headers to remove sensitive information like API keys.
     */
    private String sanitizeHeaders(String headers) {
        return headers
                .replaceAll("(?i)(api-key|authorization|x-api-key|bearer):\\s*[^\\n]+", "$1: [REDACTED]")
                .replaceAll("(?i)(api_key|apikey)=[^&\\n]+", "$1=[REDACTED]");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [truncated, " + text.length() + " total chars]";
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
            System.err.println("Failed to write to API log: " + e.getMessage());
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
}
