package ape.marketingdepartment.service.browser;

import ape.marketingdepartment.model.BrowserSettings;
import ape.marketingdepartment.model.PublishingProfile;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Manages Chrome WebDriver instances with persistent user profiles.
 *
 * Uses persistent browser profiles so users only need to log in once.
 * Subsequent sessions reuse the saved cookies/session data.
 */
public class BrowserManager {

    private final BrowserSettings browserSettings;

    public BrowserManager(BrowserSettings browserSettings) {
        this.browserSettings = browserSettings;
        // Setup ChromeDriver automatically
        WebDriverManager.chromedriver().setup();
    }

    /**
     * Create a new WebDriver instance for the given publishing profile.
     * The profile's browser data directory is used for persistent login.
     */
    public WebDriver createDriver(PublishingProfile profile) {
        ChromeOptions options = new ChromeOptions();

        // Set Chrome binary path if specified
        String chromePath = browserSettings.getChromePath();
        if (chromePath != null && !chromePath.isEmpty()) {
            Path chromeFile = Path.of(chromePath);
            if (Files.exists(chromeFile)) {
                options.setBinary(chromePath);
            }
        }

        // Set up persistent user data directory
        String profilePath = expandPath(profile.getBrowserProfilePath());
        try {
            Files.createDirectories(Path.of(profilePath));
        } catch (Exception e) {
            System.err.println("Warning: Could not create profile directory: " + e.getMessage());
        }
        options.addArguments("--user-data-dir=" + profilePath);

        // Headless mode if configured (may not work for all sites due to bot detection)
        if (browserSettings.isHeadless()) {
            options.addArguments("--headless=new");
        }

        // Common options for stability
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        // Avoid detection
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

        return driver;
    }

    /**
     * Open browser for manual login to save session.
     * User logs in manually, then closes the browser.
     */
    public void openForLogin(PublishingProfile profile, String loginUrl) {
        WebDriver driver = null;
        try {
            driver = createDriver(profile);
            driver.get(loginUrl);

            // Browser stays open for user to log in manually
            // User closes browser when done
            // Session data is saved in the profile directory

            // Wait until user closes the browser
            while (true) {
                try {
                    driver.getTitle(); // Check if browser is still open
                    Thread.sleep(1000);
                } catch (Exception e) {
                    // Browser was closed
                    break;
                }
            }
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Expand ~ to user home directory in path.
     */
    private String expandPath(String path) {
        if (path == null) return null;
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
