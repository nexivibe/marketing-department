package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

public class ApiKey {
    private String service;
    private String key;
    private String description;

    public ApiKey() {
    }

    public ApiKey(String service, String key, String description) {
        this.service = service;
        this.key = key;
        this.description = description;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static ApiKey fromJson(String json) {
        ApiKey apiKey = new ApiKey();
        apiKey.service = JsonHelper.extractStringField(json, "service");
        apiKey.key = JsonHelper.extractStringField(json, "key");
        apiKey.description = JsonHelper.extractStringField(json, "description");
        return apiKey;
    }

    public String toJson() {
        return "{\n" +
                "      \"service\": " + JsonHelper.toJsonString(service) + ",\n" +
                "      \"key\": " + JsonHelper.toJsonString(key) + ",\n" +
                "      \"description\": " + JsonHelper.toJsonString(description) + "\n" +
                "    }";
    }

    @Override
    public String toString() {
        return service + " - " + (description != null ? description : "");
    }
}
