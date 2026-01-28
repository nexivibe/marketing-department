package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

public class BrowserSettings {
    private String chromePath;
    private boolean headless;

    public BrowserSettings() {
        this.chromePath = "/usr/bin/google-chrome";
        this.headless = false;
    }

    public BrowserSettings(String chromePath, boolean headless) {
        this.chromePath = chromePath;
        this.headless = headless;
    }

    public String getChromePath() {
        return chromePath;
    }

    public void setChromePath(String chromePath) {
        this.chromePath = chromePath;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public static BrowserSettings fromJson(String json) {
        BrowserSettings settings = new BrowserSettings();
        String chromePath = JsonHelper.extractStringField(json, "chromePath");
        if (chromePath != null) {
            settings.chromePath = chromePath;
        }
        Boolean headless = JsonHelper.extractBooleanField(json, "headless");
        if (headless != null) {
            settings.headless = headless;
        }
        return settings;
    }

    public String toJson() {
        return "{\n" +
                "    \"chromePath\": " + JsonHelper.toJsonString(chromePath) + ",\n" +
                "    \"headless\": " + headless + "\n" +
                "  }";
    }
}
