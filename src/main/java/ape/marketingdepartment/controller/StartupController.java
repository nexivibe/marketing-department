package ape.marketingdepartment.controller;

import ape.marketingdepartment.MarketingApp;
import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Project;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
        Path path = toPath(lastPath);
        if (path != null && Files.exists(path)) {
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
            Path path = toPath(projectPath);
            if (path != null && Files.exists(path)) {
                recentProjectsList.getItems().add(projectPath);
            }
        }

        recentProjectsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = recentProjectsList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    Path path = toPath(selected);
                    if (path != null) {
                        openProject(path);
                    }
                }
            }
        });
    }

    @FXML
    private void onLastProjectClick() {
        String lastPath = settings.getLastProjectPath();
        Path path = toPath(lastPath);
        if (path != null) {
            openProject(path);
        }
    }

    @FXML
    private void onOpenProjectClick() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Project Folder");

        String lastPath = settings.getLastProjectPath();
        Path lastProjectPath = toPath(lastPath);
        if (lastProjectPath != null) {
            Path parent = lastProjectPath.getParent();
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
            ErrorDialog.showDetailed("Failed to Create Project",
                    "Could not create project at: " + selectedDir.getPath(),
                    formatException(e));
        }
    }

    private void openProject(Path path) {
        try {
            Project project = Project.loadFromFolder(path);
            app.showProjectView(project);
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Project",
                    "Could not open project at: " + path,
                    formatException(e));
        }
    }

    @FXML
    private void onSettingsClick() {
        try {
            app.showSettingsDialog();
        } catch (IOException e) {
            ErrorDialog.showDetailed("Failed to Open Settings",
                    "Could not open the settings dialog.",
                    formatException(e));
        }
    }

    private String formatException(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error: ").append(e.getClass().getSimpleName()).append("\n");
        sb.append("Message: ").append(e.getMessage()).append("\n\n");
        sb.append("Stack Trace:\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        if (e.getCause() != null) {
            sb.append("\nCaused by: ").append(e.getCause().getClass().getSimpleName()).append("\n");
            sb.append("Message: ").append(e.getCause().getMessage()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Converts a path string to a Path object, handling file: URI format if present.
     * This is needed because paths might be stored as URIs in some cases.
     */
    private Path toPath(String pathString) {
        if (pathString == null) {
            return null;
        }

        // Handle file: URI format (e.g., "file:/C/Users/..." or "file:///C:/Users/...")
        if (pathString.startsWith("file:")) {
            try {
                // Normalize the URI - some formats may be malformed
                String normalized = pathString;

                // Fix common malformed patterns:
                // "file:/C/..." -> "file:///C:/..."
                if (pathString.matches("file:/[A-Za-z]/.*")) {
                    char drive = pathString.charAt(6);
                    normalized = "file:///" + drive + ":/" + pathString.substring(8);
                }
                // "file://C/..." -> "file:///C:/..."
                else if (pathString.matches("file://[A-Za-z]/.*")) {
                    char drive = pathString.charAt(7);
                    normalized = "file:///" + drive + ":/" + pathString.substring(9);
                }

                URI uri = new URI(normalized);
                return Path.of(uri);
            } catch (Exception e) {
                // If URI parsing fails, try stripping the file: prefix
                String stripped = pathString.replaceFirst("^file:/*", "");
                // Add drive letter colon if missing on Windows (e.g., "C/Users" -> "C:/Users")
                if (stripped.matches("[A-Za-z]/.*")) {
                    stripped = stripped.charAt(0) + ":/" + stripped.substring(2);
                }
                return Path.of(stripped);
            }
        }

        return Path.of(pathString);
    }
}
