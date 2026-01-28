package ape.marketingdepartment.service.publishing;

import ape.marketingdepartment.model.PublishingProfile;
import ape.marketingdepartment.service.browser.BrowserManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * LinkedIn publishing service using Selenium browser automation.
 *
 * Note: Browser automation is fragile - if LinkedIn changes their UI,
 * the CSS selectors may need updating. Check this class if publishing stops working.
 */
public class LinkedInPublisher implements PublishingService {

    private static final String FEED_URL = "https://www.linkedin.com/feed/";
    private static final String LOGIN_URL = "https://www.linkedin.com/login";

    // CSS Selectors - May need updating if LinkedIn changes their UI
    private static final String START_POST_BUTTON = "button.share-box-feed-entry__trigger";
    private static final String POST_EDITOR = "div.ql-editor[data-placeholder]";
    private static final String POST_BUTTON = "button.share-actions__primary-action";

    private final BrowserManager browserManager;

    public LinkedInPublisher(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    @Override
    public String getPlatformName() {
        return "linkedin";
    }

    @Override
    public CompletableFuture<PublishResult> publish(PublishingProfile profile, String content) {
        return CompletableFuture.supplyAsync(() -> {
            WebDriver driver = null;
            try {
                driver = browserManager.createDriver(profile);
                driver.get(FEED_URL);

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

                // Wait for and click "Start a post" button
                WebElement startPostButton = wait.until(
                        ExpectedConditions.elementToBeClickable(By.cssSelector(START_POST_BUTTON))
                );
                startPostButton.click();

                // Wait for post editor to appear
                Thread.sleep(1000); // Allow modal to open
                WebElement editor = wait.until(
                        ExpectedConditions.elementToBeClickable(By.cssSelector(POST_EDITOR))
                );

                // Enter the content
                editor.click();
                editor.sendKeys(content);

                // Wait a moment for content to be registered
                Thread.sleep(500);

                // Click Post button
                WebElement postButton = wait.until(
                        ExpectedConditions.elementToBeClickable(By.cssSelector(POST_BUTTON))
                );
                postButton.click();

                // Wait for post to complete
                Thread.sleep(3000);

                return PublishResult.success(driver.getCurrentUrl());

            } catch (Exception e) {
                return PublishResult.failure("LinkedIn publishing failed: " + e.getMessage());
            } finally {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    @Override
    public void openForLogin(PublishingProfile profile) {
        browserManager.openForLogin(profile, LOGIN_URL);
    }
}
