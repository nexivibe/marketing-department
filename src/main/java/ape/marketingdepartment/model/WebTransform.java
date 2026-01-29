package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a web transform for a post - the URI and export status.
 */
public class WebTransform {
    private String uri;           // e.g., "my-post-title.html"
    private long timestamp;       // Last modified timestamp
    private boolean exported;     // Whether HTML has been exported
    private String lastExportPath; // Full path of last export
    private String verificationCode; // Code injected into HTML for verification

    public WebTransform() {
        this.uri = "";
        this.timestamp = 0;
        this.exported = false;
        this.lastExportPath = "";
        this.verificationCode = "";
    }

    public WebTransform(String uri, long timestamp, boolean exported, String lastExportPath) {
        this.uri = uri;
        this.timestamp = timestamp;
        this.exported = exported;
        this.lastExportPath = lastExportPath;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isExported() {
        return exported;
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }

    public String getLastExportPath() {
        return lastExportPath;
    }

    public void setLastExportPath(String lastExportPath) {
        this.lastExportPath = lastExportPath;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    /**
     * Ensure the URI ends with .html
     */
    public void normalizeUri() {
        if (uri != null && !uri.isEmpty() && !uri.endsWith(".html")) {
            uri = uri + ".html";
        }
    }

    /**
     * Generate a slug from a title for SEO-friendly URIs.
     */
    public static String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "post";
        }

        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "") // Remove special chars
                .replaceAll("\\s+", "-")          // Replace spaces with hyphens
                .replaceAll("-+", "-")            // Collapse multiple hyphens
                .replaceAll("^-|-$", "")          // Trim leading/trailing hyphens
                + ".html";
    }

    public static WebTransform fromJson(String json) {
        WebTransform transform = new WebTransform();

        String uri = JsonHelper.extractStringField(json, "uri");
        if (uri != null) {
            transform.uri = uri;
        }

        Long timestamp = JsonHelper.extractLongField(json, "timestamp");
        if (timestamp != null) {
            transform.timestamp = timestamp;
        }

        Boolean exported = JsonHelper.extractBooleanField(json, "exported");
        if (exported != null) {
            transform.exported = exported;
        }

        String lastExportPath = JsonHelper.extractStringField(json, "lastExportPath");
        if (lastExportPath != null) {
            transform.lastExportPath = lastExportPath;
        }

        String verificationCode = JsonHelper.extractStringField(json, "verificationCode");
        if (verificationCode != null) {
            transform.verificationCode = verificationCode;
        }

        return transform;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"uri\": ").append(JsonHelper.toJsonString(uri));
        sb.append(", \"timestamp\": ").append(timestamp);
        sb.append(", \"exported\": ").append(exported);
        sb.append(", \"lastExportPath\": ").append(JsonHelper.toJsonString(lastExportPath));
        sb.append(", \"verificationCode\": ").append(JsonHelper.toJsonString(verificationCode));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Load web transform from the transforms file.
     */
    public static WebTransform load(Path postsDir, String postName) {
        Path transformsFile = postsDir.resolve(postName + "-transforms.json");
        if (!Files.exists(transformsFile)) {
            return null;
        }

        try {
            String content = Files.readString(transformsFile);
            String webJson = JsonHelper.extractObjectField(content, "web");
            if (webJson != null) {
                return fromJson(webJson);
            }
        } catch (IOException e) {
            System.err.println("Failed to load web transform: " + e.getMessage());
        }

        return null;
    }

    /**
     * Save web transform to the transforms file, preserving other platform transforms.
     */
    public void save(Path postsDir, String postName) throws IOException {
        Path transformsFile = postsDir.resolve(postName + "-transforms.json");

        // Load existing transforms
        Map<String, String> platformTransforms = new HashMap<>();
        if (Files.exists(transformsFile)) {
            String content = Files.readString(transformsFile);

            // Extract existing platform transforms
            String linkedinJson = JsonHelper.extractObjectField(content, "linkedin");
            if (linkedinJson != null) {
                platformTransforms.put("linkedin", linkedinJson);
            }
            String twitterJson = JsonHelper.extractObjectField(content, "twitter");
            if (twitterJson != null) {
                platformTransforms.put("twitter", twitterJson);
            }
        }

        // Build new JSON with web transform
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        int i = 0;
        for (Map.Entry<String, String> entry : platformTransforms.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            i++;
        }

        if (i > 0) sb.append(",\n");
        sb.append("  \"web\": ").append(this.toJson());

        sb.append("\n}");

        Files.writeString(transformsFile, sb.toString());
    }

    /**
     * Get platform completion status from transforms file.
     * For web export, also verifies the exported file actually exists on disk.
     */
    public static Map<String, Boolean> getPlatformStatus(Path postsDir, String postName) {
        Map<String, Boolean> status = new HashMap<>();
        status.put("linkedin", false);
        status.put("twitter", false);
        status.put("web", false);

        Path transformsFile = postsDir.resolve(postName + "-transforms.json");
        if (!Files.exists(transformsFile)) {
            return status;
        }

        try {
            String content = Files.readString(transformsFile);

            // Check LinkedIn
            String linkedinJson = JsonHelper.extractObjectField(content, "linkedin");
            if (linkedinJson != null) {
                Boolean approved = JsonHelper.extractBooleanField(linkedinJson, "approved");
                status.put("linkedin", approved != null && approved);
            }

            // Check Twitter
            String twitterJson = JsonHelper.extractObjectField(content, "twitter");
            if (twitterJson != null) {
                Boolean approved = JsonHelper.extractBooleanField(twitterJson, "approved");
                status.put("twitter", approved != null && approved);
            }

            // Check Web - must verify file actually exists
            String webJson = JsonHelper.extractObjectField(content, "web");
            if (webJson != null) {
                Boolean exported = JsonHelper.extractBooleanField(webJson, "exported");
                String lastExportPath = JsonHelper.extractStringField(webJson, "lastExportPath");

                // Only mark as exported if file exists on disk
                boolean fileExists = false;
                if (lastExportPath != null && !lastExportPath.isEmpty()) {
                    fileExists = Files.exists(Path.of(lastExportPath));
                }

                status.put("web", exported != null && exported && fileExists);
            }
        } catch (IOException e) {
            System.err.println("Failed to load platform status: " + e.getMessage());
        }

        return status;
    }

    /**
     * Check if the exported file actually exists on disk.
     */
    public boolean exportedFileExists() {
        if (!exported || lastExportPath == null || lastExportPath.isEmpty()) {
            return false;
        }
        return Files.exists(Path.of(lastExportPath));
    }
}
