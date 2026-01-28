package ape.marketingdepartment.service.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs all AI interactions to an ai.log file in the project directory.
 */
public class AiLogger {

    private static final String LOG_FILE = "ai.log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logFile;

    public AiLogger(Path projectDir) {
        this.logFile = projectDir.resolve(LOG_FILE);
    }

    /**
     * Log an AI request.
     */
    public void logRequest(String service, String model, String systemPrompt, String userContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(80)).append("\n");
        sb.append("[").append(timestamp()).append("] REQUEST to ").append(service).append("\n");
        sb.append("Model: ").append(model).append("\n");
        sb.append("-".repeat(40)).append(" SYSTEM PROMPT ").append("-".repeat(25)).append("\n");
        sb.append(systemPrompt).append("\n");
        sb.append("-".repeat(40)).append(" USER CONTENT ").append("-".repeat(26)).append("\n");
        sb.append(userContent).append("\n");
        sb.append("-".repeat(80)).append("\n");

        write(sb.toString());
    }

    /**
     * Log an AI response.
     */
    public void logResponse(String service, int statusCode, String responseBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] RESPONSE from ").append(service).append("\n");
        sb.append("Status: ").append(statusCode).append("\n");
        sb.append("-".repeat(40)).append(" RESPONSE BODY ").append("-".repeat(25)).append("\n");
        sb.append(responseBody).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        write(sb.toString());
    }

    /**
     * Log an error.
     */
    public void logError(String service, String errorMessage, Throwable exception) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("] ERROR from ").append(service).append("\n");
        sb.append("Message: ").append(errorMessage).append("\n");
        if (exception != null) {
            sb.append("Exception: ").append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");
        }
        sb.append("=".repeat(80)).append("\n\n");

        write(sb.toString());
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
            System.err.println("Failed to write to AI log: " + e.getMessage());
        }
    }
}
