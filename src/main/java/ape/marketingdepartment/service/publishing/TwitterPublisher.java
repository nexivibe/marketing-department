package ape.marketingdepartment.service.publishing;

import ape.marketingdepartment.model.PublishingProfile;
import ape.marketingdepartment.service.browser.BrowserManager;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * X/Twitter publishing service using Selenium browser automation.
 *
 * Note: Browser automation is fragile - if X/Twitter changes their UI,
 * the CSS selectors may need updating. Check this class if publishing stops working.
 */
public class TwitterPublisher implements PublishingService {

    private static final String COMPOSE_URL = "https://x.com/compose/tweet";
    private static final String LOGIN_URL = "https://x.com/login";

    // CSS Selectors - May need updating if X/Twitter changes their UI
    // X/Twitter uses dynamic class names, so we use aria labels and roles
    private static final String TWEET_BOX = "div[data-testid='tweetTextarea_0']";
    private static final String POST_BUTTON = "button[data-testid='tweetButton']";

    private final BrowserManager browserManager;

    public TwitterPublisher(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    @Override
    public String getPlatformName() {
        return "twitter";
    }

    @Override
    public CompletableFuture<PublishResult> publish(PublishingProfile profile, String content) {
        return CompletableFuture.supplyAsync(() -> {
            WebDriver driver = null;
            try {
                driver = browserManager.createDriver(profile);
                driver.get(COMPOSE_URL);

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

                // Wait for tweet composer to load
                WebElement tweetBox = wait.until(
                        ExpectedConditions.elementToBeClickable(By.cssSelector(TWEET_BOX))
                );

                // Click and enter content
                tweetBox.click();
                Thread.sleep(500);

                // Type the content (sendKeys handles special characters better than direct input)
                tweetBox.sendKeys(content);

                // Wait for content to register
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
                return PublishResult.failure("X/Twitter publishing failed: " + e.getMessage());
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
