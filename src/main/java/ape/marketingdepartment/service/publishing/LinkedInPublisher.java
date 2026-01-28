package ape.marketingdepartment.service.publishing;

import ape.marketingdepartment.model.PublishingProfile;
import ape.marketingdepartment.service.browser.BrowserManager;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LinkedIn publishing service using Selenium browser automation.
 *
 * Note: Browser automation is fragile - if LinkedIn changes their UI,
 * the CSS selectors may need updating. Check this class if publishing stops working.
 *
 * Last updated: January 2026
 */
public class LinkedInPublisher implements PublishingService {

    private static final String FEED_URL = "https://www.linkedin.com/feed/";
    private static final String LOGIN_URL = "https://www.linkedin.com/login";

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
        return publish(profile, content, status -> {});
    }

    @Override
    public CompletableFuture<PublishResult> publish(PublishingProfile profile, String content, Consumer<String> statusListener) {
        return CompletableFuture.supplyAsync(() -> {
            WebDriver driver = null;
            try {
                statusListener.accept("Starting Chrome browser...");
                driver = browserManager.createDriver(profile);

                statusListener.accept("Navigating to LinkedIn feed...");
                driver.get(FEED_URL);

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

                // Wait for page to fully load
                statusListener.accept("Waiting for page to load...");
                Thread.sleep(3000);

                // Check if we're logged in by looking for the feed or login form
                if (isLoginPage(driver)) {
                    return PublishResult.failure("Not logged in to LinkedIn. Please use 'Login' to authenticate first.");
                }

                // Find and click "Start a post" - this is the text input area at the top
                statusListener.accept("Looking for 'Start a post' button...");
                WebElement startPostButton = findStartPostButton(driver, wait, statusListener);

                statusListener.accept("Clicking 'Start a post'...");
                startPostButton.click();

                // Wait for post modal to open
                statusListener.accept("Waiting for post editor modal...");
                Thread.sleep(2000);

                // Find the text editor in the modal
                statusListener.accept("Looking for text editor...");
                WebElement editor = findPostEditor(driver, wait, statusListener);

                // Click the editor to focus it
                statusListener.accept("Focusing text editor...");
                editor.click();
                Thread.sleep(500);

                // Enter the content
                statusListener.accept("Entering post content...");
                editor.sendKeys(content);
                Thread.sleep(1000);

                // Find and click the Post button
                statusListener.accept("Looking for Post button...");
                WebElement postButton = findPostButton(driver, wait, statusListener);

                statusListener.accept("Clicking Post button...");
                postButton.click();

                // Wait for post to complete
                statusListener.accept("Waiting for post to publish...");
                Thread.sleep(4000);

                statusListener.accept("Published successfully!");
                return PublishResult.success(driver.getCurrentUrl());

            } catch (Exception e) {
                statusListener.accept("Error: " + e.getMessage());
                return PublishResult.failure("LinkedIn publishing failed: " + e.getMessage());
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
        return url.contains("/login") || url.contains("/checkpoint");
    }

    private WebElement findStartPostButton(WebDriver driver, WebDriverWait wait, Consumer<String> statusListener) {
        // LinkedIn's "Start a post" is typically a button or clickable div at the top of the feed
        // We want to avoid clicking on media upload buttons
        String[] selectors = {
                // Primary: the text-like input at top of feed
                "button.share-box-feed-entry__trigger",
                "div.share-box-feed-entry__trigger",
                // The "Start a post" button specifically
                "button[aria-label='Start a post']",
                "button[aria-label='Create a post']",
                // Fallback: share box trigger area (avoid media buttons)
                ".share-box-feed-entry__top-bar button:first-child",
                ".share-box-feed-entry__avatar-wrapper + button",
        };

        return findElement(driver, wait, selectors, "Start a post button", statusListener);
    }

    private WebElement findPostEditor(WebDriver driver, WebDriverWait wait, Consumer<String> statusListener) {
        // The post editor is a contenteditable div inside the modal
        String[] selectors = {
                // Main editor div (quill-based)
                "div.ql-editor[contenteditable='true']",
                // Role-based selector
                "div[role='textbox'][contenteditable='true']",
                "div[aria-label='Text editor for creating content']",
                // Data attribute selectors
                "div[data-placeholder*='want to talk about']",
                ".share-creation-state__text-editor div[contenteditable='true']",
                // Generic contenteditable in share modal
                ".share-box div[contenteditable='true']",
                ".editor-content[contenteditable='true']",
        };

        return findElement(driver, wait, selectors, "post editor", statusListener);
    }

    private WebElement findPostButton(WebDriver driver, WebDriverWait wait, Consumer<String> statusListener) {
        // The Post button is the primary action button in the share modal
        String[] selectors = {
                // Primary action button
                "button.share-actions__primary-action",
                ".share-box_actions button.artdeco-button--primary",
                // By text content
                "button.artdeco-button--primary span:contains('Post')",
                // By aria-label
                "button[aria-label='Post']",
                // Generic primary button in share actions
                ".share-actions button.artdeco-button--primary",
                ".share-box__footer button.artdeco-button--primary",
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
                    // Found elements, check if any are visible and clickable
                    for (WebElement element : elements) {
                        if (element.isDisplayed() && element.isEnabled()) {
                            // Verify it's not a file input or media button
                            String tagName = element.getTagName().toLowerCase();
                            String type = element.getAttribute("type");
                            if ("input".equals(tagName) && "file".equals(type)) {
                                statusListener.accept("Skipping file input element");
                                continue;
                            }

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

        // Try one more time with wait
        for (String selector : selectors) {
            try {
                WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
                statusListener.accept("Found " + elementName + " with wait: " + selector);
                return element;
            } catch (Exception ignored) {
            }
        }

        throw new RuntimeException("Could not find " + elementName + ". Tried selectors:" + tried +
                "\n\nLinkedIn may have changed their UI. Please report this issue.");
    }

    @Override
    public void openForLogin(PublishingProfile profile) {
        browserManager.openForLogin(profile, LOGIN_URL);
    }
}
