package ape.marketingdepartment.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AppSettings {
    private static final Path SETTINGS_DIR = Path.of(System.getProperty("user.home"), ".marketing-department");
    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve("settings.json");
    private static final int MAX_RECENT_PROJECTS = 10;

    private String lastProjectPath;
    private List<String> recentProjects;

    public AppSettings() {
        this.recentProjects = new ArrayList<>();
    }

    public String getLastProjectPath() {
        return lastProjectPath;
    }

    public void setLastProjectPath(String lastProjectPath) {
        this.lastProjectPath = lastProjectPath;
    }

    public List<String> getRecentProjects() {
        return recentProjects;
    }

    public void addRecentProject(String projectPath) {
        recentProjects.remove(projectPath);
        recentProjects.addFirst(projectPath);
        if (recentProjects.size() > MAX_RECENT_PROJECTS) {
            recentProjects = new ArrayList<>(recentProjects.subList(0, MAX_RECENT_PROJECTS));
        }
        this.lastProjectPath = projectPath;
    }

    public static AppSettings load() {
        if (!Files.exists(SETTINGS_FILE)) {
            return new AppSettings();
        }
        try {
            String content = Files.readString(SETTINGS_FILE);
            return parseJson(content);
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
            return new AppSettings();
        }
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_DIR);
            String json = toJson();
            Files.writeString(SETTINGS_FILE, json);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    private static AppSettings parseJson(String json) {
        AppSettings settings = new AppSettings();

        String lastPath = extractStringField(json, "lastProjectPath");
        if (lastPath != null) {
            settings.lastProjectPath = lastPath;
        }

        int recentStart = json.indexOf("\"recentProjects\"");
        if (recentStart != -1) {
            int arrayStart = json.indexOf('[', recentStart);
            int arrayEnd = json.indexOf(']', arrayStart);
            if (arrayStart != -1 && arrayEnd != -1) {
                String arrayContent = json.substring(arrayStart + 1, arrayEnd);
                String[] parts = arrayContent.split(",");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                        settings.recentProjects.add(unescapeJson(trimmed.substring(1, trimmed.length() - 1)));
                    }
                }
            }
        }

        return settings;
    }

    private static String extractStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int fieldStart = json.indexOf(pattern);
        if (fieldStart == -1) return null;

        int colonPos = json.indexOf(':', fieldStart);
        if (colonPos == -1) return null;

        int valueStart = json.indexOf('"', colonPos);
        if (valueStart == -1) return null;

        int valueEnd = findClosingQuote(json, valueStart + 1);
        if (valueEnd == -1) return null;

        return unescapeJson(json.substring(valueStart + 1, valueEnd));
    }

    private static int findClosingQuote(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '"' && json.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"lastProjectPath\": ");
        if (lastProjectPath != null) {
            sb.append("\"").append(escapeJson(lastProjectPath)).append("\"");
        } else {
            sb.append("null");
        }
        sb.append(",\n");
        sb.append("  \"recentProjects\": [");
        for (int i = 0; i < recentProjects.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJson(recentProjects.get(i))).append("\"");
        }
        sb.append("]\n");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
