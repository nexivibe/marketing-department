package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Post;
import ape.marketingdepartment.model.Project;
import ape.marketingdepartment.model.ProjectSettings;
import ape.marketingdepartment.service.MustacheEngine;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the project settings dialog.
 */
public class ProjectSettingsDialogController {

    @FXML private ComboBox<String> agentComboBox;
    @FXML private TextField defaultAuthorField;
    @FXML private TextArea reviewerPromptArea;
    @FXML private TextArea rewritePromptArea;
    @FXML private TextField urlBaseField;
    @FXML private TextField postTemplateField;
    @FXML private TextField tagIndexTemplateField;
    @FXML private TextField listingTemplateField;
    @FXML private TextField listingOutputPatternField;
    @FXML private Spinner<Integer> postsPerPageSpinner;
    @FXML private TextField webExportDirField;
    @FXML private TextArea tagSuggestionPromptArea;
    @FXML private TextArea uriSuggestionPromptArea;
    @FXML private TextArea descriptionSuggestionPromptArea;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Stage dialogStage;
    private Project project;
    private AppSettings appSettings;
    private boolean saved = false;

    @FXML
    private void initialize() {
        // Initialize agent combo box
        agentComboBox.getItems().addAll("grok");

        // Initialize posts per page spinner
        SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 10);
        postsPerPageSpinner.setValueFactory(valueFactory);

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

    public void setAppSettings(AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    private void loadSettings() {
        ProjectSettings settings = project.getSettings();
        agentComboBox.setValue(settings.getSelectedAgent());
        defaultAuthorField.setText(settings.getDefaultAuthor());
        reviewerPromptArea.setText(settings.getReviewerPrompt());
        rewritePromptArea.setText(settings.getRewritePrompt());

        // Web publishing settings
        urlBaseField.setText(settings.getUrlBase() != null ? settings.getUrlBase() : "");
        postTemplateField.setText(settings.getPostTemplate() != null ? settings.getPostTemplate() : "post-template.html");
        tagIndexTemplateField.setText(settings.getTagIndexTemplate() != null ? settings.getTagIndexTemplate() : "tag-index-template.html");
        listingTemplateField.setText(settings.getListingTemplate() != null ? settings.getListingTemplate() : "listing-template.html");
        listingOutputPatternField.setText(settings.getListingOutputPattern() != null ? settings.getListingOutputPattern() : "blog-");
        postsPerPageSpinner.getValueFactory().setValue(settings.getPostsPerPage() > 0 ? settings.getPostsPerPage() : 10);
        webExportDirField.setText(settings.getWebExportDirectory() != null ? settings.getWebExportDirectory() : "./public");
        tagSuggestionPromptArea.setText(settings.getTagSuggestionPrompt() != null ? settings.getTagSuggestionPrompt() : "");
        uriSuggestionPromptArea.setText(settings.getUriSuggestionPrompt() != null ? settings.getUriSuggestionPrompt() : "");
        descriptionSuggestionPromptArea.setText(settings.getDescriptionSuggestionPrompt() != null ? settings.getDescriptionSuggestionPrompt() : "");
    }

    private void onSave() {
        try {
            ProjectSettings settings = project.getSettings();
            settings.setSelectedAgent(agentComboBox.getValue());
            settings.setDefaultAuthor(defaultAuthorField.getText().trim());
            settings.setReviewerPrompt(reviewerPromptArea.getText());
            settings.setRewritePrompt(rewritePromptArea.getText());

            // Web publishing settings - ensure URL base ends with /
            String urlBase = urlBaseField.getText().trim();
            if (!urlBase.isEmpty() && !urlBase.endsWith("/")) {
                urlBase = urlBase + "/";
                urlBaseField.setText(urlBase);
            }
            settings.setUrlBase(urlBase);

            settings.setPostTemplate(postTemplateField.getText().trim());
            settings.setTagIndexTemplate(tagIndexTemplateField.getText().trim());
            settings.setListingTemplate(listingTemplateField.getText().trim());
            settings.setListingOutputPattern(listingOutputPatternField.getText().trim());
            settings.setPostsPerPage(postsPerPageSpinner.getValue());
            settings.setWebExportDirectory(webExportDirField.getText().trim());
            settings.setTagSuggestionPrompt(tagSuggestionPromptArea.getText());
            settings.setUriSuggestionPrompt(uriSuggestionPromptArea.getText());
            settings.setDescriptionSuggestionPrompt(descriptionSuggestionPromptArea.getText());

            project.saveSettings();
            saved = true;
            dialogStage.close();
        } catch (IOException e) {
            showError("Failed to Save Settings", e.getMessage());
        }
    }

    // Template editing methods

    @FXML
    private void onEditTemplate() {
        openTemplateEditor(
                postTemplateField.getText(),
                "Post Template",
                MustacheEngine.generateDefaultTemplate(),
                TemplateType.POST
        );
    }

    @FXML
    private void onEditTagIndexTemplate() {
        openTemplateEditor(
                tagIndexTemplateField.getText(),
                "Tag Index Template",
                MustacheEngine.generateDefaultTagIndexTemplate(),
                TemplateType.TAG_INDEX
        );
    }

    @FXML
    private void onEditListingTemplate() {
        openTemplateEditor(
                listingTemplateField.getText(),
                "Listing Template",
                MustacheEngine.generateDefaultListingTemplate(),
                TemplateType.LISTING
        );
    }

    private void openTemplateEditor(String templateFile, String title, String defaultTemplate, TemplateType type) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/template-editor-dialog.fxml")
            );
            Parent root = loader.load();

            Stage editorStage = new Stage();
            editorStage.setTitle(title + " - " + templateFile);
            editorStage.initModality(Modality.WINDOW_MODAL);
            editorStage.initOwner(dialogStage);
            editorStage.setScene(new Scene(root));
            editorStage.setResizable(true);

            // Get a sample post for preview, or null if no posts exist
            Post samplePost = null;
            List<Post> posts = project.getPosts();
            if (!posts.isEmpty()) {
                samplePost = posts.getFirst();
            }

            TemplateEditorDialogController controller = loader.getController();
            controller.initializeWithType(editorStage, appSettings, project, samplePost, templateFile, defaultTemplate, type);

            editorStage.showAndWait();

        } catch (IOException e) {
            showError("Failed to Open Template Editor", e.getMessage());
        }
    }

    // Browse methods

    @FXML
    private void onBrowseTemplate() {
        browseTemplateFile(postTemplateField, "Post Template");
    }

    @FXML
    private void onBrowseTagIndexTemplate() {
        browseTemplateFile(tagIndexTemplateField, "Tag Index Template");
    }

    @FXML
    private void onBrowseListingTemplate() {
        browseTemplateFile(listingTemplateField, "Listing Template");
    }

    private void browseTemplateFile(TextField targetField, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select " + title + " File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("HTML Files", "*.html", "*.htm"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File projectDir = project.getPath().toFile();
        if (projectDir.exists()) {
            chooser.setInitialDirectory(projectDir);
        }

        File selectedFile = chooser.showOpenDialog(dialogStage);
        if (selectedFile != null) {
            String pathToUse = askRelativeOrAbsolute(selectedFile.toPath(), title.toLowerCase());
            if (pathToUse != null) {
                targetField.setText(pathToUse);
            }
        }
    }

    @FXML
    private void onBrowseExportDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Export Directory");

        String currentDir = webExportDirField.getText().trim();
        File initialDir = project.getPath().toFile();

        if (!currentDir.isEmpty()) {
            Path currentPath = Path.of(currentDir);
            if (!currentPath.isAbsolute()) {
                currentPath = project.getPath().resolve(currentDir);
            }
            if (currentPath.toFile().exists()) {
                initialDir = currentPath.toFile();
            }
        }

        if (initialDir.exists()) {
            chooser.setInitialDirectory(initialDir);
        }

        File selectedDir = chooser.showDialog(dialogStage);
        if (selectedDir != null) {
            String pathToUse = askRelativeOrAbsolute(selectedDir.toPath(), "export directory");
            if (pathToUse != null) {
                webExportDirField.setText(pathToUse);
            }
        }
    }

    /**
     * Ask the user whether to use relative or absolute path.
     */
    private String askRelativeOrAbsolute(Path selectedPath, String itemName) {
        Path projectPath = project.getPath().toAbsolutePath();
        Path absolutePath = selectedPath.toAbsolutePath();

        String relativePath = null;
        try {
            Path relative = projectPath.relativize(absolutePath);
            String relativeStr = relative.toString();
            if (!relativeStr.startsWith("..") || relativeStr.split("\\.\\.").length <= 3) {
                relativePath = "./" + relativeStr.replace("\\", "/");
            }
        } catch (IllegalArgumentException e) {
            // Can't create relative path
        }

        if (relativePath == null) {
            return absolutePath.toString();
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Path Type");
        alert.setHeaderText("Choose path type for " + itemName);
        alert.setResizable(true);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(10));

        Label infoLabel = new Label("A relative path works well in Git repositories.");
        infoLabel.setWrapText(true);

        Label relLabel = new Label("Relative:");
        relLabel.setStyle("-fx-font-weight: bold;");
        TextField relField = new TextField(relativePath);
        relField.setEditable(false);
        relField.setStyle("-fx-background-color: #f0f0f0;");

        Label absLabel = new Label("Absolute:");
        absLabel.setStyle("-fx-font-weight: bold;");
        TextField absField = new TextField(absolutePath.toString());
        absField.setEditable(false);
        absField.setStyle("-fx-background-color: #f0f0f0;");

        content.getChildren().addAll(infoLabel, relLabel, relField, absLabel, absField);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinWidth(500);

        ButtonType relativeButton = new ButtonType("Use Relative");
        ButtonType absoluteButton = new ButtonType("Use Absolute");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(relativeButton, absoluteButton, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == relativeButton) {
                return relativePath;
            } else if (result.get() == absoluteButton) {
                return absolutePath.toString();
            }
        }
        return null;
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

    /**
     * Enum to identify template types for the editor.
     */
    public enum TemplateType {
        POST,
        TAG_INDEX,
        LISTING
    }
}
