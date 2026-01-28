package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

/**
 * Represents a Grok AI model with its metadata.
 */
public class GrokModel {
    private String id;
    private String ownedBy;
    private String description;

    public GrokModel() {
    }

    public GrokModel(String id, String ownedBy, String description) {
        this.id = id;
        this.ownedBy = ownedBy;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get display text for UI lists.
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder(id);
        if (description != null && !description.isEmpty()) {
            sb.append(" - ").append(description);
        } else if (ownedBy != null && !ownedBy.isEmpty()) {
            sb.append(" (").append(ownedBy).append(")");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getDisplayText();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":").append(JsonHelper.toJsonString(id));
        if (ownedBy != null) {
            sb.append(",\"ownedBy\":").append(JsonHelper.toJsonString(ownedBy));
        }
        if (description != null) {
            sb.append(",\"description\":").append(JsonHelper.toJsonString(description));
        }
        sb.append("}");
        return sb.toString();
    }

    public static GrokModel fromJson(String json) {
        GrokModel model = new GrokModel();
        model.id = JsonHelper.extractStringField(json, "id");
        model.ownedBy = JsonHelper.extractStringField(json, "ownedBy");
        // Also try owned_by for API response format
        if (model.ownedBy == null) {
            model.ownedBy = JsonHelper.extractStringField(json, "owned_by");
        }
        model.description = JsonHelper.extractStringField(json, "description");
        return model;
    }
}
