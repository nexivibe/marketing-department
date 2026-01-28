package ape.marketingdepartment.controller;

import ape.marketingdepartment.service.ai.GrokService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

/**
 * Utility class for showing detailed error dialogs with copyable content.
 */
public class ErrorDialog {

    /**
     * Show a simple error message.
     */
    public static void show(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show a detailed error dialog with expandable/collapsible details section.
     * Details are hidden by default behind a "Show Details" button.
     */
    public static void showDetailed(String title, String summary, String details) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(true);

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setPrefWidth(500);

        // Summary label (not selectable, brief description)
        Label summaryLabel = new Label(summary);
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-font-size: 14px;");

        // Details section (initially hidden)
        VBox detailsBox = new VBox(8);
        detailsBox.setManaged(false);
        detailsBox.setVisible(false);

        Label detailsLabel = new Label("Error Details:");
        detailsLabel.setStyle("-fx-font-weight: bold;");

        // Details text area
        TextArea detailsArea = new TextArea(details);
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(12);
        detailsArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        // Copy button
        Button copyButton = new Button("Copy to Clipboard");
        copyButton.setOnAction(e -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(details);
            Clipboard.getSystemClipboard().setContent(clipboardContent);

            // Show feedback
            copyButton.setText("Copied!");
            copyButton.setDisable(true);
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                }
                javafx.application.Platform.runLater(() -> {
                    copyButton.setText("Copy to Clipboard");
                    copyButton.setDisable(false);
                });
            }).start();
        });

        detailsBox.getChildren().addAll(detailsLabel, detailsArea, copyButton);

        // Toggle button for show/hide details
        Button toggleButton = new Button("Show Details");
        toggleButton.setOnAction(e -> {
            boolean showing = detailsBox.isVisible();
            detailsBox.setVisible(!showing);
            detailsBox.setManaged(!showing);
            toggleButton.setText(showing ? "Show Details" : "Hide Details");

            // Resize dialog to fit content
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        });

        content.getChildren().addAll(summaryLabel, toggleButton, detailsBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

        dialog.showAndWait();
    }

    /**
     * Show an error dialog for AI service exceptions with full request/response details.
     */
    public static void showAiError(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && !(cause instanceof GrokService.AiServiceException)) {
            cause = cause.getCause();
        }

        if (cause instanceof GrokService.AiServiceException aiEx) {
            showDetailed(
                    "AI Service Error",
                    "HTTP " + aiEx.getStatusCode() + ": " + aiEx.getErrorDetail(),
                    aiEx.getFullDetails()
            );
        } else {
            // Generic error
            String message = cause.getMessage();
            if (message == null) {
                message = cause.getClass().getName();
            }
            showDetailed(
                    "Error",
                    "An error occurred",
                    message + "\n\n" + getStackTrace(cause)
            );
        }
    }

    private static String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append("Stack Trace:\n");
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
