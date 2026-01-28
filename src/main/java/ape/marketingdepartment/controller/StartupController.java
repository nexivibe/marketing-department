package ape.marketingdepartment.controller;

import ape.marketingdepartment.MarketingApp;
import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Project;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class StartupController {
    @FXML private Button lastProjectButton;
    @FXML private ListView<String> recentProjectsList;
    @FXML private Button openProjectButton;
    @FXML private Button createProjectButton;
    @FXML private Button settingsButton;

    private MarketingApp app;
    private AppSettings settings;

    public void initialize(MarketingApp app, AppSettings settings) {
        this.app = app;
        this.settings = settings;

        setupLastProjectButton();
        setupRecentProjectsList();
    }

    private void setupLastProjectButton() {
        String lastPath = settings.getLastProjectPath();
        if (lastPath != null && Files.exists(Path.of(lastPath))) {
            Path path = Path.of(lastPath);
            lastProjectButton.setText("Open: " + path.getFileName().toString());
            lastProjectButton.setVisible(true);
            lastProjectButton.setManaged(true);
        } else {
            lastProjectButton.setVisible(false);
            lastProjectButton.setManaged(false);
        }
    }

    private void setupRecentProjectsList() {
        recentProjectsList.getItems().clear();

        for (String projectPath : settings.getRecentProjects()) {
            if (Files.exists(Path.of(projectPath))) {
                recentProjectsList.getItems().add(projectPath);
            }
        }

        recentProjectsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = recentProjectsList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openProject(Path.of(selected));
                }
            }
        });
    }

    @FXML
    private void onLastProjectClick() {
        String lastPath = settings.getLastProjectPath();
        if (lastPath != null) {
            openProject(Path.of(lastPath));
        }
    }

    @FXML
    private void onOpenProjectClick() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Project Folder");

        String lastPath = settings.getLastProjectPath();
        if (lastPath != null) {
            Path parent = Path.of(lastPath).getParent();
            if (parent != null && Files.exists(parent)) {
                chooser.setInitialDirectory(parent.toFile());
            }
        }

        File selectedDir = chooser.showDialog(app.getPrimaryStage());
        if (selectedDir != null) {
            openProject(selectedDir.toPath());
        }
    }

    @FXML
    private void onCreateProjectClick() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Location");

        File selectedDir = chooser.showDialog(app.getPrimaryStage());
        if (selectedDir == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selectedDir.getName());
        dialog.setTitle("Create New Project");
        dialog.setHeaderText("Enter project title:");
        dialog.setContentText("Title:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        String title = result.get().trim();

        try {
            Project project = Project.create(selectedDir.toPath(), title);
            app.showProjectView(project);
        } catch (IOException e) {
            showError("Failed to Create Project", e.getMessage());
        }
    }

    private void openProject(Path path) {
        try {
            Project project = Project.loadFromFolder(path);
            app.showProjectView(project);
        } catch (IOException e) {
            showError("Failed to Open Project", e.getMessage());
        }
    }

    @FXML
    private void onSettingsClick() {
        try {
            app.showSettingsDialog();
        } catch (IOException e) {
            showError("Failed to Open Settings", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
