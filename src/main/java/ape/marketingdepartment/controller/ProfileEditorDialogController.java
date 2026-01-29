package ape.marketingdepartment.controller;

import ape.marketingdepartment.model.*;
import ape.marketingdepartment.model.pipeline.AuthMethod;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Optional;

/**
 * Controller for the publishing profile editor dialog.
 */
public class ProfileEditorDialogController {

    @FXML private ListView<PublishingProfile> profilesListView;
    @FXML private VBox detailsPane;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> platformCombo;
    @FXML private ComboBox<AuthMethod> authMethodCombo;
    @FXML private TextField browserProfileField;
    @FXML private CheckBox includeUrlCheckBox;
    @FXML private ComboBox<String> urlPlacementCombo;
    @FXML private Label statusLabel;

    private Stage dialogStage;
    private AppSettings appSettings;
    private PublishingProfile currentProfile;

    public void initialize(Stage dialogStage, AppSettings appSettings) {
        this.dialogStage = dialogStage;
        this.appSettings = appSettings;

        setupUI();
        refreshProfilesList();
    }

    private void setupUI() {
        dialogStage.setTitle("Publishing Profiles");

        // Setup platform combo
        platformCombo.getItems().addAll("linkedin", "twitter");

        // Setup auth method combo
        authMethodCombo.getItems().addAll(AuthMethod.values());

        // Setup URL placement combo
        urlPlacementCombo.getItems().addAll("start", "end");
        urlPlacementCombo.setValue("end");

        // Setup list view
        profilesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PublishingProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                if (empty || profile == null) {
                    setText(null);
                } else {
                    setText(profile.getName() + " (" + profile.getPlatform() + ")");
                }
            }
        });

        profilesListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> loadProfile(newVal)
        );

        // Initially hide details
        detailsPane.setDisable(true);
    }

    private void refreshProfilesList() {
        profilesListView.getItems().clear();
        profilesListView.getItems().addAll(appSettings.getPublishingProfiles());
    }

    private void loadProfile(PublishingProfile profile) {
        currentProfile = profile;

        if (profile == null) {
            detailsPane.setDisable(true);
            clearFields();
            return;
        }

        detailsPane.setDisable(false);

        nameField.setText(profile.getName());
        platformCombo.setValue(profile.getPlatform());
        authMethodCombo.setValue(profile.getAuthMethod());
        browserProfileField.setText(profile.getBrowserProfilePath());
        includeUrlCheckBox.setSelected(profile.includesUrl());
        urlPlacementCombo.setValue(profile.getUrlPlacement());
    }

    private void clearFields() {
        nameField.setText("");
        platformCombo.setValue(null);
        authMethodCombo.setValue(AuthMethod.MANUAL_BROWSER);
        browserProfileField.setText("");
        includeUrlCheckBox.setSelected(false);
        urlPlacementCombo.setValue("end");
    }

    @FXML
    private void onAddProfile() {
        TextInputDialog dialog = new TextInputDialog("New Profile");
        dialog.setTitle("Add Profile");
        dialog.setHeaderText("Enter profile name:");
        dialog.setContentText("Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.trim().isEmpty()) {
                statusLabel.setText("Profile name cannot be empty");
                return;
            }

            PublishingProfile profile = new PublishingProfile();
            profile.setName(name.trim());
            profile.setPlatform("linkedin"); // Default
            profile.setAuthMethod(AuthMethod.MANUAL_BROWSER);

            appSettings.addPublishingProfile(profile);
            appSettings.save();

            refreshProfilesList();
            profilesListView.getSelectionModel().select(profile);
            statusLabel.setText("Profile added: " + name);
        });
    }

    @FXML
    private void onRemoveProfile() {
        PublishingProfile selected = profilesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a profile to remove");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Profile");
        confirm.setHeaderText("Remove profile: " + selected.getName() + "?");
        confirm.setContentText("This cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            appSettings.removePublishingProfile(selected);
            appSettings.save();

            refreshProfilesList();
            currentProfile = null;
            clearFields();
            detailsPane.setDisable(true);
            statusLabel.setText("Profile removed");
        }
    }

    @FXML
    private void onBrowseBrowserProfile() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Browser Profile Directory");

        File selectedDir = chooser.showDialog(dialogStage);
        if (selectedDir != null) {
            browserProfileField.setText(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    private void onApplyChanges() {
        if (currentProfile == null) {
            statusLabel.setText("No profile selected");
            return;
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            statusLabel.setText("Profile name cannot be empty");
            return;
        }

        String platform = platformCombo.getValue();
        if (platform == null) {
            statusLabel.setText("Please select a platform");
            return;
        }

        currentProfile.setName(name);
        currentProfile.setPlatform(platform);
        currentProfile.setAuthMethod(authMethodCombo.getValue());
        currentProfile.setBrowserProfilePath(browserProfileField.getText().trim());
        currentProfile.setSetting("includeUrl", String.valueOf(includeUrlCheckBox.isSelected()));
        currentProfile.setSetting("urlPlacement", urlPlacementCombo.getValue());

        appSettings.save();
        refreshProfilesList();

        // Re-select the profile
        profilesListView.getSelectionModel().select(currentProfile);

        statusLabel.setText("Changes saved");
    }

    @FXML
    private void onClose() {
        dialogStage.close();
    }
}
