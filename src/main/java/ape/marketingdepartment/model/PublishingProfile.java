package ape.marketingdepartment.model;

import ape.marketingdepartment.service.JsonHelper;

public class PublishingProfile {
    private String name;
    private String platform;
    private String browserProfilePath;

    public PublishingProfile() {
    }

    public PublishingProfile(String name, String platform, String browserProfilePath) {
        this.name = name;
        this.platform = platform;
        this.browserProfilePath = browserProfilePath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getBrowserProfilePath() {
        return browserProfilePath;
    }

    public void setBrowserProfilePath(String browserProfilePath) {
        this.browserProfilePath = browserProfilePath;
    }

    public static PublishingProfile fromJson(String json) {
        PublishingProfile profile = new PublishingProfile();
        profile.name = JsonHelper.extractStringField(json, "name");
        profile.platform = JsonHelper.extractStringField(json, "platform");
        profile.browserProfilePath = JsonHelper.extractStringField(json, "browserProfilePath");
        return profile;
    }

    public String toJson() {
        return "{\n" +
                "      \"name\": " + JsonHelper.toJsonString(name) + ",\n" +
                "      \"platform\": " + JsonHelper.toJsonString(platform) + ",\n" +
                "      \"browserProfilePath\": " + JsonHelper.toJsonString(browserProfilePath) + "\n" +
                "    }";
    }

    @Override
    public String toString() {
        return name + " (" + platform + ")";
    }
}
