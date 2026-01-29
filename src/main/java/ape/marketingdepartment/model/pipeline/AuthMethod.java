package ape.marketingdepartment.model.pipeline;

/**
 * Authentication method for publishing profiles.
 */
public enum AuthMethod {
    /**
     * User manually opens browser and logs in.
     */
    MANUAL_BROWSER,

    /**
     * API key based authentication.
     */
    API_KEY,

    /**
     * OAuth based authentication (future).
     */
    OAUTH;

    public static AuthMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL_BROWSER;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MANUAL_BROWSER;
        }
    }
}
