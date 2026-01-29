package ape.marketingdepartment.service.pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for verifying URL liveness and content verification codes.
 */
public class UrlVerificationService {

    private static final String VERIFICATION_COMMENT_PREFIX = "<!-- mktdept-verify:";
    private static final String VERIFICATION_COMMENT_SUFFIX = " -->";
    private static final Pattern VERIFICATION_PATTERN = Pattern.compile(
            "<!--\\s*mktdept-verify:([a-zA-Z0-9]+)\\s*-->"
    );
    private static final int TIMEOUT_SECONDS = 30;
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final HttpClient httpClient;
    private final SecureRandom random;

    public UrlVerificationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.random = new SecureRandom();
    }

    /**
     * Generate a unique verification code for a deployment.
     */
    public String generateVerificationCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Create the verification comment to inject into HTML.
     */
    public String createVerificationComment(String code) {
        return VERIFICATION_COMMENT_PREFIX + code + VERIFICATION_COMMENT_SUFFIX;
    }

    /**
     * Verify that a URL is live and optionally check the verification code.
     *
     * @param url               The full URL to check
     * @param expectedCode      The expected verification code (null to skip code check)
     * @param requireCodeMatch  Whether to require the code to match
     * @return Verification result
     */
    public VerificationResult verify(String url, String expectedCode, boolean requireCodeMatch) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .header("User-Agent", "MarketingDepartment/1.0")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                return VerificationResult.failed("HTTP " + statusCode + " response");
            }

            if (statusCode >= 300) {
                return VerificationResult.failed("Unexpected redirect (HTTP " + statusCode + ")");
            }

            // URL is live
            if (expectedCode == null || !requireCodeMatch) {
                return VerificationResult.success(url, null, "URL is live (HTTP " + statusCode + ")");
            }

            // Check for verification code in content
            String content = response.body();
            String foundCode = extractVerificationCode(content);

            if (foundCode == null) {
                return VerificationResult.warning(url, null, "URL is live but no verification code found");
            }

            if (!foundCode.equals(expectedCode)) {
                return VerificationResult.warning(url, foundCode,
                        "URL is live but verification code mismatch (expected: " + expectedCode +
                                ", found: " + foundCode + ") - content may be stale");
            }

            return VerificationResult.success(url, foundCode,
                    "URL is live and verification code matches");

        } catch (IOException e) {
            return VerificationResult.failed("Connection failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationResult.failed("Request interrupted");
        } catch (IllegalArgumentException e) {
            return VerificationResult.failed("Invalid URL: " + e.getMessage());
        }
    }

    /**
     * Simple liveness check without code verification.
     */
    public VerificationResult checkLiveness(String url) {
        return verify(url, null, false);
    }

    /**
     * Build the full URL from base and URI.
     * URL base is expected to end with / (normalized by ProjectSettings).
     */
    public String buildFullUrl(String urlBase, String uri) {
        if (urlBase == null || urlBase.isBlank()) {
            return null;
        }

        // Ensure base ends with / (should already be normalized, but be safe)
        String base = urlBase.endsWith("/") ? urlBase : urlBase + "/";

        // Remove leading / from uri since base ends with /
        String path = uri.startsWith("/") ? uri.substring(1) : uri;

        return base + path;
    }

    /**
     * Extract verification code from HTML content.
     */
    private String extractVerificationCode(String html) {
        if (html == null) {
            return null;
        }
        Matcher matcher = VERIFICATION_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Result of URL verification.
     */
    public record VerificationResult(
            boolean success,
            boolean warning,
            String url,
            String foundCode,
            String message
    ) {
        public static VerificationResult success(String url, String code, String message) {
            return new VerificationResult(true, false, url, code, message);
        }

        public static VerificationResult warning(String url, String code, String message) {
            return new VerificationResult(true, true, url, code, message);
        }

        public static VerificationResult failed(String message) {
            return new VerificationResult(false, false, null, null, message);
        }

        public boolean isLive() {
            return success;
        }

        public boolean hasWarning() {
            return warning;
        }
    }
}
