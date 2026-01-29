package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.ApiKey;
import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.GrokModel;
import ape.marketingdepartment.model.PublishingProfile;
import ape.marketingdepartment.service.ai.GrokService;
import ape.marketingdepartment.service.getlate.GetLateAccount;
import ape.marketingdepartment.service.getlate.GetLateService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

public class SettingsController {
    @FXML private ListView<ApiKey> apiKeysList;
    @FXML private ListView<PublishingProfile> profilesList;
    @FXML private ListView<GetLateAccount> getLateAccountsList;
    @FXML private ComboBox<GrokModel> grokModelCombo;
    @FXML private Button refreshModelsButton;
    @FXML private Button refreshAccountsButton;
    @FXML private Label modelDescriptionLabel;
    @FXML private Label modelStatusLabel;
    @FXML private Label accountsStatusLabel;

    private AppSettings settings;
    private Stage dialogStage;
    private GetLateService getLateService;

    public void initialize(AppSettings settings, Stage dialogStage) {
        this.settings = settings;
        this.dialogStage = dialogStage;
        this.getLateService = new GetLateService(settings);

        loadSettings();
    }

    private void loadSettings() {
        apiKeysList.getItems().clear();
        apiKeysList.getItems().addAll(settings.getApiKeys());

        profilesList.getItems().clear();
        profilesList.getItems().addAll(settings.getPublishingProfiles());

        // Load Grok model selection
        loadGrokModels();

        // Setup model selection change listener
        grokModelCombo.setOnAction(e -> {
            GrokModel selected = grokModelCombo.getValue();
            if (selected != null) {
                updateModelDescription(selected);
            }
        });

        // Check GetLate status
        updateGetLateStatus();
    }

    private void loadGrokModels() {
        grokModelCombo.getItems().clear();

        // Load cached models
        List<GrokModel> cachedModels = settings.getCachedGrokModels();

        if (cachedModels.isEmpty()) {
            // Add default models if no cache exists
            cachedModels = List.of(
                    new GrokModel("grok-2", "xai", "Latest Grok 2 model"),
                    new GrokModel("grok-2-mini", "xai", "Smaller, faster Grok 2 variant"),
                    new GrokModel("grok-beta", "xai", "Legacy beta model")
            );
            modelStatusLabel.setText("Using default models - click Refresh to fetch available models");
        } else {
            modelStatusLabel.setText("Cached models loaded - click Refresh to update");
        }

        grokModelCombo.getItems().addAll(cachedModels);

        // Select the configured model
        String selectedModelId = settings.getSelectedGrokModel();
        if (selectedModelId != null) {
            for (GrokModel model : grokModelCombo.getItems()) {
                if (model.getId().equals(selectedModelId)) {
                    grokModelCombo.setValue(model);
                    updateModelDescription(model);
                    break;
                }
            }
        }

        // If no model selected yet, select first one
        if (grokModelCombo.getValue() == null && !grokModelCombo.getItems().isEmpty()) {
            grokModelCombo.setValue(grokModelCombo.getItems().get(0));
            updateModelDescription(grokModelCombo.getValue());
        }
    }

    private void updateModelDescription(GrokModel model) {
        if (model == null) {
            modelDescriptionLabel.setText("");
            return;
        }

        StringBuilder desc = new StringBuilder();
        if (model.getDescription() != null && !model.getDescription().isEmpty()) {
            desc.append(model.getDescription());
        }
        if (model.getOwnedBy() != null && !model.getOwnedBy().isEmpty()) {
            if (desc.length() > 0) desc.append(" ");
            desc.append("(by ").append(model.getOwnedBy()).append(")");
        }
        modelDescriptionLabel.setText(desc.toString());
    }

    private void updateGetLateStatus() {
        if (getLateService.isConfigured()) {
            accountsStatusLabel.setText("GetLate API configured - click Refresh to load accounts");
            refreshAccountsButton.setDisable(false);
        } else {
            accountsStatusLabel.setText("Add a 'getlate' API key to enable social publishing");
            refreshAccountsButton.setDisable(true);
        }
    }

    @FXML
    private void onRefreshModels() {
        // Check if API key is configured
        ApiKey grokKey = settings.getApiKeyForService("grok");
        if (grokKey == null || grokKey.getKey() == null || grokKey.getKey().isEmpty()) {
            showError("API Key Required", "Please add a Grok API key first to fetch available models.");
            return;
        }

        refreshModelsButton.setDisable(true);
        modelStatusLabel.setText("Fetching models from API...");

        GrokService grokService = new GrokService(settings);
        grokService.fetchAvailableModels()
                .thenAccept(models -> {
                    Platform.runLater(() -> {
                        refreshModelsButton.setDisable(false);

                        if (models.isEmpty()) {
                            modelStatusLabel.setText("No models returned from API");
                            return;
                        }

                        // Remember current selection
                        GrokModel previousSelection = grokModelCombo.getValue();
                        String previousId = previousSelection != null ? previousSelection.getId() : null;

                        // Update combo box
                        grokModelCombo.getItems().clear();
                        grokModelCombo.getItems().addAll(models);

                        // Restore selection if possible
                        boolean foundPrevious = false;
                        if (previousId != null) {
                            for (GrokModel model : models) {
                                if (model.getId().equals(previousId)) {
                                    grokModelCombo.setValue(model);
                                    updateModelDescription(model);
                                    foundPrevious = true;
                                    break;
                                }
                            }
                        }

                        // Select first model if previous not found
                        if (!foundPrevious && !models.isEmpty()) {
                            grokModelCombo.setValue(models.get(0));
                            updateModelDescription(models.get(0));
                        }

                        modelStatusLabel.setText("Found " + models.size() + " models - cached for future use");
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        refreshModelsButton.setDisable(false);
                        modelStatusLabel.setText("Failed to fetch models");
                        showError("Failed to Fetch Models", ex.getMessage());
                    });
                    return null;
                });
    }

    @FXML
    private void onRefreshAccounts() {
        if (!getLateService.isConfigured()) {
            showError("API Key Required", "Please add a GetLate API key first.");
            return;
        }

        refreshAccountsButton.setDisable(true);
        accountsStatusLabel.setText("Fetching accounts from GetLate...");

        getLateService.fetchAccounts()
                .thenAccept(accounts -> {
                    Platform.runLater(() -> {
                        refreshAccountsButton.setDisable(false);
                        getLateAccountsList.getItems().clear();
                        getLateAccountsList.getItems().addAll(accounts);

                        if (accounts.isEmpty()) {
                            accountsStatusLabel.setText("No accounts found - connect accounts at getlate.dev");
                        } else {
                            accountsStatusLabel.setText("Found " + accounts.size() + " accounts - double-click to create profile");
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        refreshAccountsButton.setDisable(false);
                        accountsStatusLabel.setText("Failed to fetch accounts");
                        showError("Failed to Fetch Accounts", ex.getMessage());
                    });
                    return null;
                });
    }

    @FXML
    private void onAddApiKey() {
        Dialog<ApiKey> dialog = createApiKeyDialog(null);
        Optional<ApiKey> result = dialog.showAndWait();
        result.ifPresent(apiKey -> {
            settings.addApiKey(apiKey);
            apiKeysList.getItems().clear();
            apiKeysList.getItems().addAll(settings.getApiKeys());
            // Refresh GetLate status
            this.getLateService = new GetLateService(settings);
            updateGetLateStatus();
        });
    }

    @FXML
    private void onEditApiKey() {
        ApiKey selected = apiKeysList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Dialog<ApiKey> dialog = createApiKeyDialog(selected);
        Optional<ApiKey> result = dialog.showAndWait();
        result.ifPresent(apiKey -> {
            settings.removeApiKey(selected);
            settings.addApiKey(apiKey);
            apiKeysList.getItems().clear();
            apiKeysList.getItems().addAll(settings.getApiKeys());
            // Refresh GetLate status
            this.getLateService = new GetLateService(settings);
            updateGetLateStatus();
        });
    }

    @FXML
    private void onRemoveApiKey() {
        ApiKey selected = apiKeysList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            settings.removeApiKey(selected);
            apiKeysList.getItems().remove(selected);
            // Refresh GetLate status
            this.getLateService = new GetLateService(settings);
            updateGetLateStatus();
        }
    }

    @FXML
    private void onAddProfile() {
        // Check if there are GetLate accounts to choose from
        if (getLateAccountsList.getItems().isEmpty()) {
            showInfo("Fetch Accounts First",
                    "Please add a GetLate API key and click 'Refresh Accounts' to load your connected social accounts.");
            return;
        }

        Dialog<PublishingProfile> dialog = createProfileDialog(null);
        Optional<PublishingProfile> result = dialog.showAndWait();
        result.ifPresent(profile -> {
            settings.addPublishingProfile(profile);
            profilesList.getItems().add(profile);
        });
    }

    @FXML
    private void onEditProfile() {
        PublishingProfile selected = profilesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Profile Selected", "Please select a profile to edit.");
            return;
        }

        Dialog<PublishingProfile> dialog = createProfileDialog(selected);
        Optional<PublishingProfile> result = dialog.showAndWait();
        result.ifPresent(updatedProfile -> {
            settings.removePublishingProfile(selected);
            settings.addPublishingProfile(updatedProfile);
            profilesList.getItems().clear();
            profilesList.getItems().addAll(settings.getPublishingProfiles());
        });
    }

    @FXML
    private void onRemoveProfile() {
        PublishingProfile selected = profilesList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            settings.removePublishingProfile(selected);
            profilesList.getItems().remove(selected);
        }
    }

    @FXML
    private void onCreateProfileFromAccount() {
        GetLateAccount selected = getLateAccountsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Account Selected", "Please select an account from the list.");
            return;
        }

        // Create a profile from the GetLate account
        PublishingProfile profile = new PublishingProfile();
        profile.setName(selected.getDisplayString());
        profile.setPlatform(selected.platform());
        profile.setGetLateAccountId(selected.id());
        profile.setGetLateUsername(selected.username());

        // Allow user to customize before saving
        Dialog<PublishingProfile> dialog = createProfileDialog(profile);
        Optional<PublishingProfile> result = dialog.showAndWait();
        result.ifPresent(p -> {
            settings.addPublishingProfile(p);
            profilesList.getItems().add(p);
        });
    }

    @FXML
    private void onSave() {
        // Save selected Grok model
        GrokModel selectedModel = grokModelCombo.getValue();
        if (selectedModel != null) {
            settings.setSelectedGrokModel(selectedModel.getId());
        }

        settings.save();
        dialogStage.close();
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    private Dialog<ApiKey> createApiKeyDialog(ApiKey existing) {
        Dialog<ApiKey> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add API Key" : "Edit API Key");
        dialog.setHeaderText("Enter API key details");

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<String> serviceCombo = new ComboBox<>();
        serviceCombo.getItems().addAll("grok", "getlate", "devto");
        serviceCombo.setEditable(true);

        TextField keyField = new TextField();
        keyField.setPromptText("API Key");
        keyField.setPrefWidth(300);

        TextField descField = new TextField();
        descField.setPromptText("Description (optional)");

        if (existing != null) {
            serviceCombo.setValue(existing.getService());
            keyField.setText(existing.getKey());
            descField.setText(existing.getDescription());
        } else {
            serviceCombo.setValue("grok");
        }

        grid.add(new Label("Service:"), 0, 0);
        grid.add(serviceCombo, 1, 0);
        grid.add(new Label("API Key:"), 0, 1);
        grid.add(keyField, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descField, 1, 2);

        // Add help text
        Label helpLabel = new Label("grok: AI for content generation (console.x.ai)\ngetlate: Social publishing (getlate.dev)");
        helpLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
        helpLabel.setWrapText(true);
        grid.add(helpLabel, 0, 3, 2, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String service = serviceCombo.getValue();
                String key = keyField.getText().trim();
                if (service == null || service.isEmpty() || key.isEmpty()) {
                    return null;
                }
                return new ApiKey(service, key, descField.getText().trim());
            }
            return null;
        });

        return dialog;
    }

    private Dialog<PublishingProfile> createProfileDialog(PublishingProfile existing) {
        Dialog<PublishingProfile> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Publishing Profile" : "Edit Publishing Profile");
        dialog.setHeaderText("Configure GetLate publishing profile");

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Profile Name");
        nameField.setPrefWidth(300);

        ComboBox<String> platformCombo = new ComboBox<>();
        platformCombo.getItems().addAll(GetLateService.getSupportedPlatforms());

        ComboBox<GetLateAccount> accountCombo = new ComboBox<>();
        accountCombo.getItems().addAll(getLateAccountsList.getItems());
        accountCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(GetLateAccount account) {
                return account != null ? account.getDisplayString() : "";
            }
            @Override
            public GetLateAccount fromString(String string) {
                return null;
            }
        });

        CheckBox includeUrlCheck = new CheckBox("Include post URL in content");
        ComboBox<String> urlPlacementCombo = new ComboBox<>();
        urlPlacementCombo.getItems().addAll("start", "end");
        urlPlacementCombo.setValue("end");

        if (existing != null) {
            nameField.setText(existing.getName());
            platformCombo.setValue(existing.getPlatform());
            includeUrlCheck.setSelected(existing.includesUrl());
            urlPlacementCombo.setValue(existing.getUrlPlacement());

            // Try to find matching account
            for (GetLateAccount account : getLateAccountsList.getItems()) {
                if (account.id().equals(existing.getGetLateAccountId())) {
                    accountCombo.setValue(account);
                    break;
                }
            }
        } else {
            platformCombo.setValue("twitter");
        }

        // Auto-select platform when account is selected
        accountCombo.setOnAction(e -> {
            GetLateAccount selected = accountCombo.getValue();
            if (selected != null) {
                platformCombo.setValue(selected.platform());
                if (nameField.getText() == null || nameField.getText().isBlank()) {
                    nameField.setText(selected.getDisplayString());
                }
            }
        });

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("GetLate Account:"), 0, 1);
        grid.add(accountCombo, 1, 1);
        grid.add(new Label("Platform:"), 0, 2);
        grid.add(platformCombo, 1, 2);
        grid.add(includeUrlCheck, 0, 3, 2, 1);
        grid.add(new Label("URL Placement:"), 0, 4);
        grid.add(urlPlacementCombo, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String name = nameField.getText().trim();
                String platform = platformCombo.getValue();
                GetLateAccount account = accountCombo.getValue();

                if (name.isEmpty() || platform == null || account == null) {
                    return null;
                }

                PublishingProfile profile;
                if (existing != null) {
                    profile = existing;
                    profile.setName(name);
                } else {
                    profile = new PublishingProfile(name, platform, account.id());
                }

                profile.setPlatform(platform);
                profile.setGetLateAccountId(account.id());
                profile.setGetLateUsername(account.username());
                profile.setSetting("includeUrl", String.valueOf(includeUrlCheck.isSelected()));
                profile.setSetting("urlPlacement", urlPlacementCombo.getValue());

                return profile;
            }
            return null;
        });

        return dialog;
    }

    private void showError(String title, String message) {
        ErrorDialog.showDetailed(title, title, message);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
