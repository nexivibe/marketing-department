package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlatformTransform {
    private String text;
    private long timestamp;
    private boolean approved;

    public PlatformTransform() {
    }

    public PlatformTransform(String text, long timestamp, boolean approved) {
        this.text = text;
        this.timestamp = timestamp;
        this.approved = approved;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public static PlatformTransform fromJson(String json) {
        PlatformTransform transform = new PlatformTransform();
        transform.text = JsonHelper.extractStringField(json, "text");
        Long ts = JsonHelper.extractLongField(json, "timestamp");
        transform.timestamp = ts != null ? ts : 0;
        Boolean app = JsonHelper.extractBooleanField(json, "approved");
        transform.approved = app != null && app;
        return transform;
    }

    public String toJson() {
        return "{\n" +
                "    \"text\": " + JsonHelper.toJsonString(text) + ",\n" +
                "    \"timestamp\": " + timestamp + ",\n" +
                "    \"approved\": " + approved + "\n" +
                "  }";
    }

    /**
     * Load all transforms for a post from the transforms file.
     */
    public static Map<String, PlatformTransform> loadAll(Path postsDir, String postName) {
        Map<String, PlatformTransform> transforms = new HashMap<>();
        Path transformsFile = postsDir.resolve(postName + "-transforms.json");

        if (!Files.exists(transformsFile)) {
            return transforms;
        }

        try {
            String content = Files.readString(transformsFile);

            // Parse each platform's transform
            String linkedinJson = JsonHelper.extractObjectField(content, "linkedin");
            if (linkedinJson != null) {
                transforms.put("linkedin", PlatformTransform.fromJson(linkedinJson));
            }

            String twitterJson = JsonHelper.extractObjectField(content, "twitter");
            if (twitterJson != null) {
                transforms.put("twitter", PlatformTransform.fromJson(twitterJson));
            }

        } catch (IOException e) {
            System.err.println("Failed to load transforms: " + e.getMessage());
        }

        return transforms;
    }

    /**
     * Save all transforms for a post to the transforms file.
     */
    public static void saveAll(Path postsDir, String postName, Map<String, PlatformTransform> transforms) throws IOException {
        Path transformsFile = postsDir.resolve(postName + "-transforms.json");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        int i = 0;
        for (Map.Entry<String, PlatformTransform> entry : transforms.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue().toJson());
            i++;
        }

        sb.append("\n}");
        Files.writeString(transformsFile, sb.toString());
    }
}
