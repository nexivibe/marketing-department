package ape.marketingdepartment.service.publishing;

import ape.marketingdepartment.model.PublishingProfile;
import ape.marketingdepartment.service.browser.BrowserManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * X/Twitter publishing service using Selenium browser automation.
 *
 * Note: Browser automation is fragile - if X/Twitter changes their UI,
 * the CSS selectors may need updating. Check this class if publishing stops working.
 *
 * Last updated: January 2026
 */
public class TwitterPublisher implements PublishingService {

    private static final String COMPOSE_URL = "https://x.com/compose/tweet";
    private static final String LOGIN_URL = "https://x.com/login";

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
        return publish(profile, content, status -> {});
    }

    @Override
    public CompletableFuture<PublishResult> publish(PublishingProfile profile, String content, Consumer<String> statusListener) {
        return CompletableFuture.supplyAsync(() -> {
            WebDriver driver = null;
            try {
                statusListener.accept("Starting Chrome browser...");
                driver = browserManager.createDriver(profile);

                statusListener.accept("Navigating to X compose...");
                driver.get(COMPOSE_URL);

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

                statusListener.accept("Waiting for page to load...");
                Thread.sleep(2000);

                // Check if redirected to login
                if (isLoginPage(driver)) {
                    return PublishResult.failure("Not logged in to X. Please use 'Login' to authenticate first.");
                }

                // Find tweet composer
                statusListener.accept("Looking for tweet composer...");
                WebElement tweetBox = findTweetBox(driver, wait, statusListener);

                statusListener.accept("Clicking tweet box...");
                tweetBox.click();
                Thread.sleep(500);

                statusListener.accept("Entering content...");
                tweetBox.sendKeys(content);
                Thread.sleep(1000);

                statusListener.accept("Looking for Post button...");
                WebElement postButton = findPostButton(driver, wait, statusListener);

                statusListener.accept("Clicking Post button...");
                postButton.click();

                statusListener.accept("Waiting for post to publish...");
                Thread.sleep(4000);

                statusListener.accept("Published successfully!");
                return PublishResult.success(driver.getCurrentUrl());

            } catch (Exception e) {
                statusListener.accept("Error: " + e.getMessage());
                return PublishResult.failure("X/Twitter publishing failed: " + e.getMessage());
            } finally {
                if (driver != null) {
                    try {
                        statusListener.accept("Closing browser...");
                        driver.quit();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private boolean isLoginPage(WebDriver driver) {
        String url = driver.getCurrentUrl();
        return url.contains("/login") || url.contains("/i/flow/login");
    }

    private WebElement findTweetBox(WebDriver driver, WebDriverWait wait, Consumer<String> statusListener) {
        String[] selectors = {
                "div[data-testid='tweetTextarea_0']",
                "div[aria-label='Post text']",
                "div[aria-label='Tweet text']",
                "div[role='textbox'][data-testid='tweetTextarea_0']",
                "div.public-DraftEditor-content[role='textbox']",
        };

        return findElement(driver, wait, selectors, "tweet composer", statusListener);
    }

    private WebElement findPostButton(WebDriver driver, WebDriverWait wait, Consumer<String> statusListener) {
        String[] selectors = {
                "button[data-testid='tweetButton']",
                "button[data-testid='tweetButtonInline']",
                "button[aria-label='Post']",
                "button[aria-label='Tweet']",
        };

        return findElement(driver, wait, selectors, "Post button", statusListener);
    }

    private WebElement findElement(WebDriver driver, WebDriverWait wait, String[] selectors, String elementName, Consumer<String> statusListener) {
        StringBuilder tried = new StringBuilder();

        for (String selector : selectors) {
            try {
                statusListener.accept("Trying selector: " + selector);
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));

                if (!elements.isEmpty()) {
                    for (WebElement element : elements) {
                        if (element.isDisplayed() && element.isEnabled()) {
                            statusListener.accept("Found " + elementName + " with selector: " + selector);
                            return element;
                        }
                    }
                    tried.append("\n  - ").append(selector).append(" (found but not visible/enabled)");
                } else {
                    tried.append("\n  - ").append(selector).append(" (not found)");
                }
            } catch (Exception e) {
                tried.append("\n  - ").append(selector).append(" (").append(e.getClass().getSimpleName()).append(")");
            }
        }

        // Try with wait
        for (String selector : selectors) {
            try {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
                statusListener.accept("Found " + elementName + " with wait: " + selector);
                return element;
            } catch (Exception ignored) {
            }
        }

        throw new RuntimeException("Could not find " + elementName + ". Tried selectors:" + tried +
                "\n\nX/Twitter may have changed their UI. Please report this issue.");
    }

    @Override
    public void openForLogin(PublishingProfile profile) {
        browserManager.openForLogin(profile, LOGIN_URL);
    }
}
