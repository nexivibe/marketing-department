package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Modality;
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
    @FXML private Label profilesCountLabel;
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

                    if (!stage.isEnabled()) {
                        setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                    } else if (stage.getType().isGatekeeper()) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        refreshStagesList();
        updateProfilesCount();
    }

    private String formatStageName(PipelineStage stage) {
        StringBuilder sb = new StringBuilder();
        sb.append(stage.getOrder() + 1).append(". ");
        sb.append(stage.getType().getDisplayName());

        if (stage.getType().isSocialStage() && stage.getProfileId() != null) {
            PublishingProfile profile = appSettings.getProfileById(stage.getProfileId());
            if (profile != null) {
                sb.append(" - ").append(profile.getName());
                sb.append(" [").append(getAuthMethodLabel(profile.getAuthMethod())).append("]");
            } else {
                sb.append(" - (Profile not found)");
            }
        }

        // Mark gatekeeper stages as required
        if (stage.getType().isGatekeeper()) {
            sb.append(" [Required]");
        }

        if (!stage.isEnabled()) {
            sb.append(" [Disabled]");
        }

        return sb.toString();
    }

    private String getAuthMethodLabel(ape.marketingdepartment.model.pipeline.AuthMethod authMethod) {
        if (authMethod == null) {
            return "Browser";
        }
        return switch (authMethod) {
            case MANUAL_BROWSER -> "Browser";
            case API_KEY -> "API";
            case OAUTH -> "OAuth";
        };
    }

    private void refreshStagesList() {
        stagesListView.getItems().clear();
        stagesListView.getItems().addAll(pipeline.getSortedStages());
    }

    private void updateProfilesCount() {
        int count = appSettings.getPublishingProfiles().size();
        profilesCountLabel.setText(count + " profile" + (count == 1 ? "" : "s") + " configured");
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
        addSocialStage(PipelineStageType.LINKEDIN, "linkedin");
    }

    @FXML
    private void onAddTwitter() {
        addSocialStage(PipelineStageType.TWITTER, "twitter");
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

    private void addSocialStage(PipelineStageType type, String platform) {
        List<PublishingProfile> profiles = appSettings.getProfilesForPlatform(platform);

        if (profiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Profiles");
            alert.setHeaderText("No " + platform + " profiles configured");
            alert.setContentText("Please add a publishing profile first via 'Manage Profiles'.");
            alert.showAndWait();
            return;
        }

        // Let user select profile
        ChoiceDialog<PublishingProfile> dialog = new ChoiceDialog<>(profiles.getFirst(), profiles);
        dialog.setTitle("Select Profile");
        dialog.setHeaderText("Select " + platform + " profile for this stage");
        dialog.setContentText("Profile:");

        Optional<PublishingProfile> result = dialog.showAndWait();
        result.ifPresent(profile -> {
            PipelineStage stage = new PipelineStage(type, profile.getId(), pipeline.getStages().size());
            stage.setStageSetting("includeUrl", String.valueOf(profile.includesUrl()));
            pipeline.addStage(stage);
            refreshStagesList();
            statusLabel.setText("Added " + type.getDisplayName() + " stage with " + profile.getName());
        });
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

        if (selected.getType().isSocialStage()) {
            // Show prompt editing dialog for social stages
            showStagePromptEditor(selected);
        } else {
            // Simple enable/disable toggle for non-social stages
            selected.setEnabled(!selected.isEnabled());
            refreshStagesList();
            statusLabel.setText(selected.isEnabled() ? "Stage enabled" : "Stage disabled");
        }
    }

    private void showStagePromptEditor(PipelineStage stage) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Stage: " + stage.getType().getDisplayName());
        dialog.setHeaderText("Edit the transform prompt for this stage");

        // Create content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(10));

        // Enabled checkbox
        CheckBox enabledCheck = new CheckBox("Stage Enabled");
        enabledCheck.setSelected(stage.isEnabled());
        content.getChildren().add(enabledCheck);

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
        resetButton.setOnAction(e -> promptArea.setText(PipelineStage.getDefaultPrompt(stage.getType())));
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
            stage.setEnabled(enabledCheck.isSelected());
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
    private void onManageProfiles() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ape/marketingdepartment/profile-editor-dialog.fxml")
            );
            javafx.scene.Parent root = loader.load();

            Stage editorStage = new Stage();
            editorStage.initModality(Modality.WINDOW_MODAL);
            editorStage.initOwner(dialogStage);
            editorStage.setScene(new javafx.scene.Scene(root));

            ProfileEditorDialogController controller = loader.getController();
            controller.initialize(editorStage, appSettings);

            editorStage.showAndWait();

            // Refresh UI
            updateProfilesCount();
            refreshStagesList();

        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Profile Editor",
                    "Could not open the profile editor dialog.",
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
