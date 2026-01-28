package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.model.ProjectSettings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controller for the project settings dialog.
 */
public class ProjectSettingsDialogController {

    @FXML private ComboBox<String> agentComboBox;
    @FXML private TextField defaultAuthorField;
    @FXML private TextArea reviewerPromptArea;
    @FXML private TextArea rewritePromptArea;
    @FXML private TextArea linkedinPromptArea;
    @FXML private TextArea twitterPromptArea;
    @FXML private TextField urlBaseField;
    @FXML private TextField postTemplateField;
    @FXML private TextField webExportDirField;
    @FXML private TextArea tagSuggestionPromptArea;
    @FXML private TextArea uriSuggestionPromptArea;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Stage dialogStage;
    private Project project;
    private boolean saved = false;

    @FXML
    private void initialize() {
        // Initialize agent combo box
        agentComboBox.getItems().addAll("grok");

        // Set up button handlers
        saveButton.setOnAction(e -> onSave());
        cancelButton.setOnAction(e -> onCancel());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setProject(Project project) {
        this.project = project;
        loadSettings();
    }

    private void loadSettings() {
        ProjectSettings settings = project.getSettings();
        agentComboBox.setValue(settings.getSelectedAgent());
        defaultAuthorField.setText(settings.getDefaultAuthor());
        reviewerPromptArea.setText(settings.getReviewerPrompt());
        rewritePromptArea.setText(settings.getRewritePrompt());
        linkedinPromptArea.setText(settings.getPlatformPrompt("linkedin"));
        twitterPromptArea.setText(settings.getPlatformPrompt("twitter"));

        // Web publishing settings
        urlBaseField.setText(settings.getUrlBase() != null ? settings.getUrlBase() : "");
        postTemplateField.setText(settings.getPostTemplate() != null ? settings.getPostTemplate() : "post-template.html");
        webExportDirField.setText(settings.getWebExportDirectory() != null ? settings.getWebExportDirectory() : "./public");
        tagSuggestionPromptArea.setText(settings.getTagSuggestionPrompt() != null ? settings.getTagSuggestionPrompt() : "");
        uriSuggestionPromptArea.setText(settings.getUriSuggestionPrompt() != null ? settings.getUriSuggestionPrompt() : "");
    }

    private void onSave() {
        try {
            ProjectSettings settings = project.getSettings();
            settings.setSelectedAgent(agentComboBox.getValue());
            settings.setDefaultAuthor(defaultAuthorField.getText().trim());
            settings.setReviewerPrompt(reviewerPromptArea.getText());
            settings.setRewritePrompt(rewritePromptArea.getText());
            settings.setPlatformPrompt("linkedin", linkedinPromptArea.getText());
            settings.setPlatformPrompt("twitter", twitterPromptArea.getText());

            // Web publishing settings
            settings.setUrlBase(urlBaseField.getText().trim());
            settings.setPostTemplate(postTemplateField.getText().trim());
            settings.setWebExportDirectory(webExportDirField.getText().trim());
            settings.setTagSuggestionPrompt(tagSuggestionPromptArea.getText());
            settings.setUriSuggestionPrompt(uriSuggestionPromptArea.getText());

            project.saveSettings();
            saved = true;
            dialogStage.close();
        } catch (IOException e) {
            showError("Failed to Save Settings", e.getMessage());
        }
    }

    private void onCancel() {
        dialogStage.close();
    }

    public boolean isSaved() {
        return saved;
    }

    private void showError(String title, String message) {
        ErrorDialog.showDetailed(title, title, message);
    }
}
