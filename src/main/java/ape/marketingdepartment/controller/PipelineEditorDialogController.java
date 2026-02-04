package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.*;
import ape.marketingdepartment.service.getlate.GetLateService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the pipeline editor dialog.
 */
public class PipelineEditorDialogController {

    @FXML private TextField pipelineNameField;
    @FXML private ListView<PipelineStage> stagesListView;
    @FXML private Button editStageButton;
    @FXML private Label statusLabel;

    private Stage dialogStage;
    private AppSettings appSettings;
    private Project project;
    private Pipeline pipeline;
    private boolean saved = false;

    public void initialize(Stage dialogStage, AppSettings appSettings, Project project) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;
        this.project = project;

        loadPipeline();
        setupUI();
    }

    private void loadPipeline() {
        pipeline = Pipeline.load(project.getPath());
    }

    private void setupUI() {
        dialogStage.setTitle("Pipeline Editor");

        pipelineNameField.setText(pipeline.getName());

        // Setup list view
        stagesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PipelineStage stage, boolean empty) {
                super.updateItem(stage, empty);
                if (empty || stage == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String text = formatStageName(stage);
                    setText(text);

                    if (stage.getType().isGatekeeper()) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Add selection listener to show/hide Edit Stage button
        stagesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateEditButtonVisibility(newVal);
        });

        refreshStagesList();
        updateEditButtonVisibility(null);
    }

    private void updateEditButtonVisibility(PipelineStage selected) {
        // Only show Edit button for stages with editable settings (those requiring transforms)
        boolean showEdit = selected != null && selected.getType().requiresTransform();
        editStageButton.setVisible(showEdit);
        editStageButton.setManaged(showEdit);
    }

    private String formatStageName(PipelineStage stage) {
        StringBuilder sb = new StringBuilder();
        sb.append(stage.getOrder() + 1).append(". ");
        sb.append(stage.getType().getDisplayName());

        if (stage.getType().isSocialStage() && stage.getProfileId() != null) {
            PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
            if (profile != null) {
                sb.append(" - ").append(profile.getName());
                sb.append(" [").append(GetLateService.getPlatformDisplayName(profile.getPlatform())).append("]");
            } else {
                sb.append(" - (Profile not found)");
            }
        }

        // Mark gatekeeper stages as required
        if (stage.getType().isGatekeeper()) {
            sb.append(" [Required]");
        }

        return sb.toString();
    }

    private void refreshStagesList() {
        stagesListView.getItems().clear();
        stagesListView.getItems().addAll(pipeline.getSortedStages());
    }

    @FXML
    private void onAddWebExport() {
        // Check if web export already exists
        if (pipeline.hasStageOfType(PipelineStageType.WEB_EXPORT)) {
            statusLabel.setText("Web Export stage already exists (only one allowed)");
            return;
        }
        addStage(PipelineStageType.WEB_EXPORT, null);
    }

    @FXML
    private void onAddUrlVerify() {
        // Check if URL verify already exists
        if (pipeline.hasStageOfType(PipelineStageType.URL_VERIFY)) {
            statusLabel.setText("URL Verify stage already exists (only one allowed)");
            return;
        }
        PipelineStage stage = new PipelineStage(PipelineStageType.URL_VERIFY, pipeline.getStages().size());
        stage.setStageSetting("requireCodeMatch", "true");
        pipeline.addStage(stage);
        refreshStagesList();
        statusLabel.setText("Added URL Verify stage");
    }

    @FXML
    private void onAddLinkedIn() {
        addGetLateStage("linkedin");
    }

    @FXML
    private void onAddTwitter() {
        addGetLateStage("twitter");
    }

    @FXML
    private void onAddBluesky() {
        addGetLateStage("bluesky");
    }

    @FXML
    private void onAddThreads() {
        addGetLateStage("threads");
    }

    @FXML
    private void onAddFacebook() {
        addGetLateStage("facebook");
    }

    @FXML
    private void onAddInstagram() {
        addGetLateStage("instagram");
    }

    @FXML
    private void onAddReddit() {
        addGetLateStage("reddit");
    }

    @FXML
    private void onAddGetLate() {
        addGetLateStage(null);
    }

    @FXML
    private void onAddDevTo() {
        addDevToStage();
    }

    @FXML
    private void onAddFacebookCopyPasta() {
        addFacebookCopyPastaStage();
    }

    @FXML
    private void onAddHackerNewsExport() {
        addHackerNewsExportStage();
    }

    private void addStage(PipelineStageType type, String profileId) {
        PipelineStage stage = new PipelineStage(type, pipeline.getStages().size());
        if (profileId != null) {
            stage.setProfileId(profileId);
        }
        pipeline.addStage(stage);
        refreshStagesList();
        statusLabel.setText("Added " + type.getDisplayName() + " stage");
    }

    private void addGetLateStage(String platformFilter) {
        List<PublishingProfile> profiles;
        if (platformFilter != null) {
            profiles = appSettings.getProfilesForPlatform(platformFilter);
        } else {
            profiles = appSettings.getPublishingProfiles();
        }

        if (profiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Profiles");
            if (platformFilter != null) {
                alert.setHeaderText("No " + platformFilter + " profiles configured");
            } else {
                alert.setHeaderText("No publishing profiles configured");
            }
            alert.setContentText("Please add a publishing profile first via Settings > GetLate Accounts.");
            alert.showAndWait();
            return;
        }

        // Let user select profile
        ChoiceDialog<PublishingProfile> dialog = new ChoiceDialog<>(profiles.getFirst(), profiles);
        dialog.setTitle("Select Profile");
        dialog.setHeaderText("Select profile for this stage");
        dialog.setContentText("Profile:");

        Optional<PublishingProfile> result = dialog.showAndWait();
        result.ifPresent(profile -> {
            PipelineStage stage = new PipelineStage(PipelineStageType.GETLATE, profile.getId(), pipeline.getStages().size());
            stage.setPlatformHint(profile.getPlatform());
            stage.setStageSetting("includeUrl", String.valueOf(profile.includesUrl()));
            // Set platform-specific default prompt
            stage.setPrompt(PipelineStage.getDefaultPromptForPlatform(profile.getPlatform()));
            pipeline.addStage(stage);
            refreshStagesList();
            statusLabel.setText("Added GetLate stage with " + profile.getName());
        });
    }

    private void addDevToStage() {
        // Check if Dev.to API key is configured
        if (appSettings.getApiKeyForService("devto") == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("API Key Required");
            alert.setHeaderText("Dev.to API key not configured");
            alert.setContentText("Please add a 'devto' API key in Settings > API Keys.\n\n" +
                    "You can get your API key from:\nhttps://dev.to/settings/extensions");
            alert.showAndWait();
            return;
        }

        PipelineStage stage = new PipelineStage(PipelineStageType.DEV_TO, pipeline.getStages().size());
        stage.setPlatformHint("devto");
        stage.setPrompt(PipelineStage.getDefaultPromptForPlatform("devto"));
        // By default, publish to Dev.to
        stage.setStageSetting("published", "true");
        // Include canonical URL to the web export
        stage.setStageSetting("includeCanonical", "true");
        pipeline.addStage(stage);
        refreshStagesList();
        statusLabel.setText("Added Dev.to Article stage");
    }

    private void addFacebookCopyPastaStage() {
        PipelineStage stage = new PipelineStage(PipelineStageType.FACEBOOK_COPY_PASTA, pipeline.getStages().size());
        stage.setPlatformHint("facebook_copy_pasta");
        stage.setPrompt(PipelineStage.getDefaultPromptForPlatform("facebook_copy_pasta"));
        pipeline.addStage(stage);
        refreshStagesList();
        statusLabel.setText("Added Facebook Copy Pasta stage");
    }

    private void addHackerNewsExportStage() {
        PipelineStage stage = new PipelineStage(PipelineStageType.HACKER_NEWS_EXPORT, pipeline.getStages().size());
        stage.setPlatformHint("hackernews");
        stage.setPrompt(PipelineStage.getDefaultPromptForPlatform("hackernews"));
        pipeline.addStage(stage);
        refreshStagesList();
        statusLabel.setText("Added Hacker News Export stage");
    }

    @FXML
    private void onMoveUp() {
        PipelineStage selected = stagesListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getOrder() == 0) {
            return;
        }

        // Gatekeeper stages (web export, url verify) cannot be moved
        if (selected.getType().isGatekeeper()) {
            statusLabel.setText("Gatekeeper stages cannot be reordered");
            return;
        }

        // Swap with previous stage (but only with other social stages)
        List<PipelineStage> stages = pipeline.getStages();
        int index = stages.indexOf(selected);
        if (index > 0) {
            PipelineStage prev = stages.get(index - 1);
            // Don't swap with gatekeeper stages
            if (prev.getType().isGatekeeper()) {
                statusLabel.setText("Cannot move above gatekeeper stages");
                return;
            }
            int prevOrder = prev.getOrder();
            prev.setOrder(selected.getOrder());
            selected.setOrder(prevOrder);
            refreshStagesList();
            stagesListView.getSelectionModel().select(selected);
        }
    }

    @FXML
    private void onMoveDown() {
        PipelineStage selected = stagesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Gatekeeper stages (web export, url verify) cannot be moved
        if (selected.getType().isGatekeeper()) {
            statusLabel.setText("Gatekeeper stages cannot be reordered");
            return;
        }

        List<PipelineStage> stages = pipeline.getStages();
        int index = stages.indexOf(selected);
        if (index < stages.size() - 1) {
            PipelineStage next = stages.get(index + 1);
            int nextOrder = next.getOrder();
            next.setOrder(selected.getOrder());
            selected.setOrder(nextOrder);
            refreshStagesList();
            stagesListView.getSelectionModel().select(selected);
        }
    }

    @FXML
    private void onEditStage() {
        PipelineStage selected = stagesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a stage to edit");
            return;
        }

        // Only stages with editable prompts can be edited (GETLATE, DEV_TO)
        if (selected.getType().requiresTransform()) {
            showStagePromptEditor(selected);
        } else {
            statusLabel.setText(selected.getType().getDisplayName() + " has no editable settings");
        }
    }

    private void showStagePromptEditor(PipelineStage stage) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Stage: " + stage.getType().getDisplayName());
        dialog.setHeaderText("Edit the transform prompt for this stage");

        // Create content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(10));

        // Prompt text area
        Label promptLabel = new Label("Transform Prompt:");
        promptLabel.setStyle("-fx-font-weight: bold;");
        content.getChildren().add(promptLabel);

        TextArea promptArea = new TextArea(stage.getEffectivePrompt());
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(8);
        promptArea.setPrefWidth(500);
        content.getChildren().add(promptArea);

        // Reset to default button
        Button resetButton = new Button("Reset to Default");
        resetButton.setOnAction(e -> promptArea.setText(PipelineStage.getDefaultPromptForPlatform(stage.getPlatformHint())));
        content.getChildren().add(resetButton);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return promptArea.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPrompt -> {
            stage.setPrompt(newPrompt);
            refreshStagesList();
            statusLabel.setText("Stage updated");
        });
    }

    @FXML
    private void onRemoveStage() {
        PipelineStage selected = stagesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a stage to remove");
            return;
        }

        // Gatekeeper stages (web export, url verify) are required and cannot be removed
        if (selected.getType().isGatekeeper()) {
            statusLabel.setText(selected.getType().getDisplayName() + " is required and cannot be removed");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Stage");
        confirm.setHeaderText("Remove " + selected.getType().getDisplayName() + "?");
        confirm.setContentText("This will remove the stage from the pipeline.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            pipeline.removeStage(selected);
            refreshStagesList();
            statusLabel.setText("Stage removed");
        }
    }

    @FXML
    private void onOpenSettings() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/settings-dialog.fxml"));
            javafx.scene.Parent root = loader.load();

            Stage settingsStage = new Stage();
            settingsStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            settingsStage.initOwner(dialogStage);
            settingsStage.setTitle("App Settings");
            settingsStage.setScene(new javafx.scene.Scene(root));

            SettingsController controller = loader.getController();
            controller.initialize(appSettings, settingsStage);

            settingsStage.showAndWait();

            // Refresh the stages list in case profiles changed
            refreshStagesList();

        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Settings",
                    "Could not open the settings dialog.",
                    e.getMessage());
        }
    }

    @FXML
    private void onSave() {
        pipeline.setName(pipelineNameField.getText().trim());

        try {
            pipeline.save(project.getPath());
            saved = true;
            statusLabel.setText("Pipeline saved");
            dialogStage.close();
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Save Pipeline",
                    "Could not save the pipeline configuration.",
                    e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    public boolean isSaved() {
        return saved;
    }
}
