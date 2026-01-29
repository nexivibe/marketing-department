package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.service.MustacheEngine;
import ape.marketingdepartment.service.TemplateValidationService;
import ape.marketingdepartment.service.WebExportService;
import ape.marketingdepartment.service.ai.AiReviewService;
import ape.marketingdepartment.service.ai.AiServiceFactory;
import ape.marketingdepartment.service.ai.AiStatus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Controller for the template editor dialog.
 * Allows editing the post template with live preview and AI assistance.
 *
 * IMPORTANT: Always validate template correctness after AI modifications.
 * The AI may generate invalid Mustache syntax or break template structure.
 * Use the Validate button before saving any AI-generated changes.
 */
public class TemplateEditorDialogController {

    @FXML private TextArea templateArea;
    @FXML private WebView previewWebView;
    @FXML private TextArea validationResultArea;
    @FXML private TextArea aiPromptArea;
    @FXML private VBox validationBox;
    @FXML private Label statusLabel;
    @FXML private Label aiStatusLabel;
    @FXML private Button validateButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button applyAiButton;
    @FXML private ProgressIndicator aiProgressIndicator;
    @FXML private ComboBox<Post> previewPostCombo;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Post selectedPost; // Currently selected post for preview
    private List<Post> allPosts;
    private String originalTemplate;
    private boolean saved = false;

    // Template type support
    private String templateFileName;
    private String defaultTemplateContent;
    private ProjectSettingsDialogController.TemplateType templateType;

    private final WebExportService webExportService = new WebExportService();
    private final TemplateValidationService validationService = new TemplateValidationService();
    private AiServiceFactory aiServiceFactory;

    /**
     * Initialize for editing post template (backward compatible).
     */
    public void initialize(Stage dialogStage, AppSettings appSettings, Project project, Post initialPost) {
        initializeWithType(
                dialogStage, appSettings, project, initialPost,
                project.getSettings().getPostTemplate(),
                MustacheEngine.generateDefaultTemplate(),
                ProjectSettingsDialogController.TemplateType.POST
        );
    }

    /**
     * Initialize for editing any template type.
     */
    public void initializeWithType(Stage dialogStage, AppSettings appSettings, Project project, Post initialPost,
                                    String templateFile, String defaultTemplate,
                                    ProjectSettingsDialogController.TemplateType type) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;
        this.templateFileName = templateFile;
        this.defaultTemplateContent = defaultTemplate;
        this.templateType = type;
        this.aiServiceFactory = new AiServiceFactory(appSettings);

        dialogStage.setTitle("Template Editor - " + templateFile);

        // Setup post dropdown
        setupPostDropdown(initialPost);

        loadTemplate();
        loadSavedAiPrompt();
        refreshPreview();

        // Track changes
        templateArea.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasChanges = !newVal.equals(originalTemplate);
            saveButton.setDisable(!hasChanges);
            statusLabel.setText(hasChanges ? "* Unsaved changes" : "");
        });
    }

    private void setupPostDropdown(Post initialPost) {
        allPosts = project.getPosts();

        // Setup converter to show post title in dropdown
        previewPostCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Post post) {
                return post != null ? post.getTitle() : "";
            }

            @Override
            public Post fromString(String string) {
                return null; // Not needed for non-editable combo
            }
        });

        previewPostCombo.getItems().addAll(allPosts);

        // Select initial post or first available
        if (initialPost != null && allPosts.contains(initialPost)) {
            previewPostCombo.setValue(initialPost);
            selectedPost = initialPost;
        } else if (!allPosts.isEmpty()) {
            previewPostCombo.setValue(allPosts.getFirst());
            selectedPost = allPosts.getFirst();
        }
    }

    @FXML
    private void onPreviewPostChanged() {
        selectedPost = previewPostCombo.getValue();
        refreshPreview();
    }

    private void loadSavedAiPrompt() {
        String savedPrompt = project.getSettings().getTemplateAiPrompt();
        if (savedPrompt != null && !savedPrompt.isBlank()) {
            aiPromptArea.setText(savedPrompt);
        }
    }

    private void saveAiPrompt() {
        String prompt = aiPromptArea.getText();
        if (prompt != null && !prompt.isBlank()) {
            project.getSettings().setTemplateAiPrompt(prompt);
            try {
                project.saveSettings();
            } catch (IOException e) {
                // Ignore - not critical
            }
        }
    }

    private void loadTemplate() {
        try {
            String template = MustacheEngine.loadOrCreateTemplate(
                    project.getPath(),
                    templateFileName,
                    defaultTemplateContent
            );
            originalTemplate = template;
            templateArea.setText(template);
            saveButton.setDisable(true);
            statusLabel.setText("");
        } catch (IOException e) {
            showError("Failed to Load Template", e.getMessage());
        }
    }

    @FXML
    private void onRefreshPreview() {
        refreshPreview();
    }

    private void refreshPreview() {
        if (selectedPost == null) {
            previewWebView.getEngine().loadContent(
                    "<html><body style='font-family: sans-serif; color: #888; padding: 20px;'>" +
                    "<p>No post selected for preview.</p><p>Select a post from the dropdown to see the template in action.</p>" +
                    "</body></html>");
            return;
        }

        try {
            // Create a temporary MustacheEngine and render with current template
            String template = templateArea.getText();

            // Use a simplified preview - just render the template
            String preview = webExportService.generatePreviewWithTemplate(project, selectedPost, template);
            previewWebView.getEngine().loadContent(preview);
        } catch (Exception e) {
            previewWebView.getEngine().loadContent(
                    "<html><body style='font-family: sans-serif; color: #c00; padding: 20px;'>" +
                    "<h3>Preview Error</h3><pre>" + escapeHtml(e.getMessage()) + "</pre>" +
                    "<p style='color: #888;'>This may indicate a template syntax error.</p>" +
                    "</body></html>");
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    @FXML
    private void onValidate() {
        String template = templateArea.getText();

        // IMPORTANT: Always validate template correctness, especially after AI modifications.
        // AI may generate invalid Mustache syntax or break the template structure.
        TemplateValidationService.ValidationResult result = validationService.validate(template);

        validationBox.setVisible(true);
        validationBox.setManaged(true);

        StringBuilder sb = new StringBuilder();
        if (result.isValid()) {
            sb.append("Template is valid.\n\n");
            sb.append("Available variables found:\n");
            for (String var : result.getVariables()) {
                sb.append("  - ").append(var).append("\n");
            }
            sb.append("\nSections found:\n");
            for (String section : result.getSections()) {
                sb.append("  - ").append(section).append("\n");
            }
            validationResultArea.setStyle("-fx-text-fill: #27ae60;");
        } else {
            sb.append("Validation errors:\n\n");
            for (String error : result.getErrors()) {
                sb.append("  - ").append(error).append("\n");
            }
            sb.append("\nWarnings:\n");
            for (String warning : result.getWarnings()) {
                sb.append("  - ").append(warning).append("\n");
            }
            validationResultArea.setStyle("-fx-text-fill: #e74c3c;");
        }
        validationResultArea.setText(sb.toString());
    }

    @FXML
    private void onApplyAi() {
        String prompt = aiPromptArea.getText();
        if (prompt == null || prompt.isBlank()) {
            aiStatusLabel.setText("Please enter a prompt describing the changes you want.");
            return;
        }

        // Save the AI prompt for next time
        saveAiPrompt();

        String agentName = project.getSettings().getSelectedAgent();
        AiReviewService service = aiServiceFactory.getService(agentName);

        if (service == null || !service.isConfigured()) {
            aiStatusLabel.setText("AI service not configured: " + agentName);
            return;
        }

        setAiLoading(true);
        aiStatusLabel.setText("Starting AI...");
        String currentTemplate = templateArea.getText();

        // Build the AI prompt for template modification
        String fullPrompt = buildTemplateModificationPrompt(prompt, currentTemplate);

        // Set up status listener for progress updates
        service.setProjectDir(project.getPath());
        service.setStatusListener(status -> Platform.runLater(() -> updateAiStatus(status)));

        service.transformContent(fullPrompt, currentTemplate)
                .thenAccept(result -> Platform.runLater(() -> {
                    // Extract just the template from the AI response
                    String newTemplate = extractTemplateFromResponse(result);
                    if (newTemplate != null && !newTemplate.isBlank()) {
                        templateArea.setText(newTemplate);
                        aiStatusLabel.setText("AI changes applied. Please validate before saving.");
                        aiStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                        // Automatically run validation after AI changes
                        onValidate();
                        // Refresh preview to show changes
                        refreshPreview();
                    } else {
                        aiStatusLabel.setText("AI did not return a valid template.");
                        aiStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    }
                    setAiLoading(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        aiStatusLabel.setText("AI error: " + ex.getMessage());
                        aiStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                        setAiLoading(false);
                    });
                    return null;
                });
    }

    private void setAiLoading(boolean loading) {
        applyAiButton.setDisable(loading);
        aiProgressIndicator.setVisible(loading);
        aiPromptArea.setDisable(loading);
    }

    private void updateAiStatus(AiStatus status) {
        switch (status.state()) {
            case IDLE -> {
                aiStatusLabel.setStyle("-fx-text-fill: #666;");
                aiStatusLabel.setText("");
            }
            case CONNECTING, SENDING -> {
                aiStatusLabel.setStyle("-fx-text-fill: #b8860b;");
                aiStatusLabel.setText(status.getDisplayText());
            }
            case WAITING, RECEIVING -> {
                aiStatusLabel.setStyle("-fx-text-fill: #1e90ff;");
                aiStatusLabel.setText(status.getDisplayText());
            }
            case PROCESSING -> {
                aiStatusLabel.setStyle("-fx-text-fill: #800080;");
                aiStatusLabel.setText(status.getDisplayText());
            }
            case COMPLETE -> {
                aiStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                aiStatusLabel.setText(status.getDisplayText());
            }
            case ERROR -> {
                aiStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                aiStatusLabel.setText(status.getDisplayText());
            }
        }
    }

    private String buildTemplateModificationPrompt(String userRequest, String currentTemplate) {
        return """
                You are a web template expert. Modify the following HTML/Mustache template according to the user's request.

                CRITICAL OUTPUT FORMAT:
                - Return ONLY the raw HTML template starting with <!DOCTYPE html>
                - Do NOT wrap in markdown code fences (no ```html or ```)
                - Do NOT include any explanations before or after the template
                - The response must be valid HTML that can be saved directly to a .html file

                PRESERVE ALL METADATA:
                - Keep ALL <meta> tags in <head> section (SEO, Open Graph, Twitter Cards)
                - Keep the JSON-LD structured data script
                - Keep {{{verificationComment}}} for deployment verification
                - Keep <link rel="canonical"> tag
                - Do NOT remove existing Mustache variables unless specifically requested

                MUSTACHE SYNTAX (preserve exactly):
                - {{variable}} - HTML-escaped variable
                - {{{variable}}} - Unescaped HTML (triple braces)
                - {{#section}}...{{/section}} - Conditional/loop section
                - {{^section}}...{{/section}} - Inverted section (if empty/false)

                AVAILABLE VARIABLES:
                - {{title}}, {{author}}, {{date}}, {{dateIso}}, {{readTime}}
                - {{description}} - SEO meta description
                - {{{content}}} - Main HTML content (unescaped, triple braces)
                - {{#tags}}...{{/tags}} - Section if tags exist
                - {{#tagsList}}{{name}}, {{url}}{{/tagsList}} - Tag iteration
                - {{canonicalUrl}}, {{siteName}}, {{ogImage}}, {{tagsCommaSeparated}}, {{wordCount}}
                - {{{verificationComment}}} - Hidden verification code

                USER REQUEST: """ + userRequest + """

                CURRENT TEMPLATE (modify and return the complete HTML):
                """ + currentTemplate;
    }

    /**
     * Extract the template from AI response.
     * AI may include explanations, so try to find just the HTML.
     */
    private String extractTemplateFromResponse(String response) {
        if (response == null) return null;

        // If response starts with <!DOCTYPE or <html, it's likely the full template
        String trimmed = response.trim();
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html") || trimmed.startsWith("<HTML")) {
            return trimmed;
        }

        // Look for HTML block in markdown code fences
        int htmlStart = response.indexOf("```html");
        if (htmlStart >= 0) {
            int codeStart = response.indexOf('\n', htmlStart) + 1;
            int codeEnd = response.indexOf("```", codeStart);
            if (codeEnd > codeStart) {
                return response.substring(codeStart, codeEnd).trim();
            }
        }

        // Look for generic code block
        int codeBlockStart = response.indexOf("```");
        if (codeBlockStart >= 0) {
            int codeStart = response.indexOf('\n', codeBlockStart) + 1;
            int codeEnd = response.indexOf("```", codeStart);
            if (codeEnd > codeStart) {
                String code = response.substring(codeStart, codeEnd).trim();
                if (code.contains("<!DOCTYPE") || code.contains("<html")) {
                    return code;
                }
            }
        }

        // Last resort: if it contains DOCTYPE or html tag, try to use as-is
        if (trimmed.contains("<!DOCTYPE") || trimmed.contains("<html")) {
            return trimmed;
        }

        return null;
    }

    @FXML
    private void onRestore() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Template");
        confirm.setHeaderText("Restore template from disk?");
        confirm.setContentText("This will discard all unsaved changes.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            loadTemplate();
            refreshPreview();
            validationBox.setVisible(false);
            validationBox.setManaged(false);
            statusLabel.setText("Template restored from disk");
        }
    }

    @FXML
    private void onResetToDefault() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset to Default Template");
        confirm.setHeaderText("Reset to default generated template?");
        confirm.setContentText("This will replace the current template with the default template.\n\n" +
                "The change will be loaded into the editor but NOT saved until you click Save.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            templateArea.setText(defaultTemplateContent);
            refreshPreview();
            validationBox.setVisible(false);
            validationBox.setManaged(false);
            statusLabel.setText("* Default template loaded (not yet saved)");
        }
    }

    @FXML
    private void onSave() {
        // Validate before saving
        TemplateValidationService.ValidationResult validation = validationService.validate(templateArea.getText());
        if (!validation.isValid()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validation Errors");
            alert.setHeaderText("Template has validation errors");
            alert.setContentText("Are you sure you want to save a template with errors?\n\n" +
                    String.join("\n", validation.getErrors()));
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
        }

        try {
            Path templatePath = project.getPath().resolve(templateFileName);
            Files.writeString(templatePath, templateArea.getText());
            originalTemplate = templateArea.getText();
            saved = true;
            saveButton.setDisable(true);
            statusLabel.setText("Template saved");
        } catch (IOException e) {
            showError("Failed to Save Template", e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        if (!templateArea.getText().equals(originalTemplate)) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Unsaved Changes");
            confirm.setHeaderText("You have unsaved changes");
            confirm.setContentText("Are you sure you want to close without saving?");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        dialogStage.close();
    }

    public boolean isSaved() {
        return saved;
    }

    private void showError(String title, String message) {
        ErrorDialog.showDetailed(title, title, message);
    }
}
