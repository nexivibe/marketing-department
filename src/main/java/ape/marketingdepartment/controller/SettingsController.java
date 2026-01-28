package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.ApiKey;
import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.BrowserSettings;
import ape.marketingdepartment.model.GrokModel;
import ape.marketingdepartment.model.PublishingProfile;
import ape.marketingdepartment.service.ai.GrokService;
import ape.marketingdepartment.service.browser.ChromeDetector;
import ape.marketingdepartment.service.publishing.PublishingService;
import ape.marketingdepartment.service.publishing.PublishingServiceFactory;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class SettingsController {
    @FXML private ListView<ApiKey> apiKeysList;
    @FXML private ListView<PublishingProfile> profilesList;
    @FXML private TextField chromePathField;
    @FXML private CheckBox headlessCheckbox;
    @FXML private ListView<String> detectedChromeList;
    @FXML private Label osLabel;
    @FXML private ComboBox<GrokModel> grokModelCombo;
    @FXML private Button refreshModelsButton;
    @FXML private Label modelDescriptionLabel;
    @FXML private Label modelStatusLabel;

    private AppSettings settings;
    private Stage dialogStage;

    public void initialize(AppSettings settings, Stage dialogStage) {
        this.settings = settings;
        this.dialogStage = dialogStage;

        loadSettings();
    }

    private void loadSettings() {
        apiKeysList.getItems().clear();
        apiKeysList.getItems().addAll(settings.getApiKeys());

        profilesList.getItems().clear();
        profilesList.getItems().addAll(settings.getPublishingProfiles());

        BrowserSettings bs = settings.getBrowserSettings();
        chromePathField.setText(bs.getChromePath());
        headlessCheckbox.setSelected(bs.isHeadless());

        // Show detected OS
        ChromeDetector.OS os = ChromeDetector.detectOS();
        osLabel.setText("Detected OS: " + os.name());

        // Setup double-click on detected Chrome list to select
        detectedChromeList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = detectedChromeList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    chromePathField.setText(selected);
                }
            }
        });

        // Load Grok model selection
        loadGrokModels();

        // Setup model selection change listener
        grokModelCombo.setOnAction(e -> {
            GrokModel selected = grokModelCombo.getValue();
            if (selected != null) {
                updateModelDescription(selected);
            }
        });
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
                    javafx.application.Platform.runLater(() -> {
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
                    javafx.application.Platform.runLater(() -> {
                        refreshModelsButton.setDisable(false);
                        modelStatusLabel.setText("Failed to fetch models");
                        showError("Failed to Fetch Models", ex.getMessage());
                    });
                    return null;
                });
    }

    @FXML
    private void onInferChrome() {
        detectedChromeList.getItems().clear();
        List<String> foundPaths = ChromeDetector.findInstalledChrome();

        if (foundPaths.isEmpty()) {
            detectedChromeList.getItems().add("(No Chrome installations found)");
        } else {
            detectedChromeList.getItems().addAll(foundPaths);
            // Auto-select first one if no path is currently set
            if (chromePathField.getText() == null || chromePathField.getText().isEmpty()) {
                chromePathField.setText(foundPaths.get(0));
            }
        }
    }

    @FXML
    private void onTestChrome() {
        String path = chromePathField.getText();
        if (path == null || path.isEmpty()) {
            showError("No Path", "Please enter or select a Chrome path first.");
            return;
        }

        if (ChromeDetector.testChromePath(path)) {
            showInfo("Chrome Found", "Chrome executable found and is accessible:\n" + path);
        } else {
            showError("Chrome Not Found", "Chrome executable not found or not accessible at:\n" + path);
        }
    }

    @FXML
    private void onAddApiKey() {
        Dialog<ApiKey> dialog = createApiKeyDialog(null);
        Optional<ApiKey> result = dialog.showAndWait();
        result.ifPresent(apiKey -> {
            settings.addApiKey(apiKey);
            apiKeysList.getItems().clear();
            apiKeysList.getItems().addAll(settings.getApiKeys());
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
        });
    }

    @FXML
    private void onRemoveApiKey() {
        ApiKey selected = apiKeysList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            settings.removeApiKey(selected);
            apiKeysList.getItems().remove(selected);
        }
    }

    @FXML
    private void onAddProfile() {
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
    private void onLoginProfile() {
        PublishingProfile selected = profilesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Profile Selected", "Please select a profile to log in.");
            return;
        }

        // Create browser profile directory if it doesn't exist
        Path profilePath = Path.of(selected.getBrowserProfilePath().replace("~", System.getProperty("user.home")));
        try {
            Files.createDirectories(profilePath);
        } catch (Exception e) {
            showError("Failed to Create Profile Directory", e.getMessage());
            return;
        }

        // Save settings first to ensure browser settings are up to date
        BrowserSettings bs = new BrowserSettings(
                chromePathField.getText(),
                headlessCheckbox.isSelected()
        );
        settings.setBrowserSettings(bs);
        settings.save();

        showInfo("Login Required",
                "A browser window will open. Please log in to " + selected.getPlatform() +
                " manually, then close the browser. Your session will be saved.\n\n" +
                "Profile path: " + profilePath);

        // Open browser for login
        PublishingServiceFactory factory = new PublishingServiceFactory(settings.getBrowserSettings());
        PublishingService service = factory.getService(selected.getPlatform());
        if (service != null) {
            // Run in background thread to not block UI
            new Thread(() -> {
                service.openForLogin(selected);
            }).start();
        }
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
    private void onBrowseChrome() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Chrome Executable");
        File file = chooser.showOpenDialog(dialogStage);
        if (file != null) {
            chromePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onSave() {
        BrowserSettings bs = new BrowserSettings(
                chromePathField.getText(),
                headlessCheckbox.isSelected()
        );
        settings.setBrowserSettings(bs);

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
        serviceCombo.getItems().addAll("grok");
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
        dialog.setHeaderText("Enter profile details");

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Profile Name");

        ComboBox<String> platformCombo = new ComboBox<>();
        platformCombo.getItems().addAll("linkedin", "twitter");

        TextField pathField = new TextField();
        pathField.setPromptText("Browser Profile Path");
        pathField.setPrefWidth(300);

        if (existing != null) {
            nameField.setText(existing.getName());
            platformCombo.setValue(existing.getPlatform());
            pathField.setText(existing.getBrowserProfilePath());
        } else {
            platformCombo.setValue("linkedin");
            pathField.setText("~/.marketing-department/browser-profiles/");
        }

        // Auto-generate profile path based on name
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                String safeName = newVal.toLowerCase().replaceAll("[^a-z0-9]", "-");
                pathField.setText("~/.marketing-department/browser-profiles/" + safeName);
            }
        });

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Platform:"), 0, 1);
        grid.add(platformCombo, 1, 1);
        grid.add(new Label("Browser Profile:"), 0, 2);
        grid.add(pathField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String name = nameField.getText().trim();
                String platform = platformCombo.getValue();
                String path = pathField.getText().trim();
                if (name.isEmpty() || platform == null || path.isEmpty()) {
                    return null;
                }
                return new PublishingProfile(name, platform, path);
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
