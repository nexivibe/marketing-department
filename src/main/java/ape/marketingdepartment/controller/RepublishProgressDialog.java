package ape.marketingdepartment.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Dialog showing progress and results during bulk web republishing.
 * Displays a live list of pages with filename, size, and time per page.
 */
public class RepublishProgressDialog {

    private final Stage stage;
    private final Label timerLabel;
    private final Label statusLabel;
    private final Label counterLabel;
    private final VBox pageListBox;
    private final ProgressIndicator spinner;
    private final Button dismissButton;
    private Timeline timer;
    private long startTime;
    private final int totalPosts;
    private int pagesCompleted;

    public RepublishProgressDialog(Window owner, int totalPosts) {
        this.totalPosts = totalPosts;
        this.pagesCompleted = 0;

        stage = new Stage();
        stage.setTitle("Republish Web Pages");
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setResizable(true);

        // Header
        Label headerLabel = new Label("Republishing " + totalPosts + " published page" + (totalPosts != 1 ? "s" : ""));
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        spinner = new ProgressIndicator();
        spinner.setPrefSize(24, 24);

        timerLabel = new Label("0.0s");
        timerLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        counterLabel = new Label("0 / " + totalPosts + " posts");
        counterLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox progressRow = new HBox(8, spinner, timerLabel, spacer, counterLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Starting...");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        statusLabel.setWrapText(true);

        // Column headers
        HBox columnHeaders = createRow("Page", "Size", "Time", true);
        columnHeaders.setStyle("-fx-border-color: transparent transparent #ccc transparent; -fx-border-width: 0 0 1 0;");

        // Scrollable page list
        pageListBox = new VBox(2);
        pageListBox.setPadding(new Insets(4, 0, 0, 0));

        ScrollPane scrollPane = new ScrollPane(pageListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        dismissButton = new Button("Dismiss");
        dismissButton.setDisable(true);
        dismissButton.setOnAction(e -> {
            stopTimer();
            stage.close();
        });

        VBox root = new VBox(10, headerLabel, progressRow, statusLabel, columnHeaders, scrollPane, dismissButton);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPrefWidth(520);
        root.setPrefHeight(450);

        Scene scene = new Scene(root);
        stage.setScene(scene);
    }

    public void show() {
        startTime = System.currentTimeMillis();
        timer = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            long seconds = elapsedMs / 1000;
            long tenths = (elapsedMs % 1000) / 100;
            timerLabel.setText(seconds + "." + tenths + "s");
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
        stage.show();
    }

    public void updateStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    public void addPageResult(String filename, long sizeBytes, long timeMs) {
        pagesCompleted++;
        String sizeStr = formatSize(sizeBytes);
        String timeStr = timeMs > 0 ? String.format("%dms", timeMs) : "-";

        Platform.runLater(() -> {
            HBox row = createRow(filename, sizeStr, timeStr, false);
            pageListBox.getChildren().add(row);
            counterLabel.setText(pagesCompleted + " / " + totalPosts + " posts");
        });
    }

    public void complete() {
        Platform.runLater(() -> {
            stopTimer();
            long elapsedMs = System.currentTimeMillis() - startTime;
            spinner.setVisible(false);
            spinner.setManaged(false);
            timerLabel.setText(String.format("Completed in %.1fs", elapsedMs / 1000.0));
            timerLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            statusLabel.setText("All pages republished successfully.");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60;");
            dismissButton.setDisable(false);
        });
    }

    public void error(String errorText) {
        Platform.runLater(() -> {
            stopTimer();
            long elapsedMs = System.currentTimeMillis() - startTime;
            spinner.setVisible(false);
            spinner.setManaged(false);
            timerLabel.setText(String.format("Failed after %.1fs", elapsedMs / 1000.0));
            timerLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");
            statusLabel.setText(errorText);
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #c0392b;");
            dismissButton.setDisable(false);
        });
    }

    private HBox createRow(String name, String size, String time, boolean isHeader) {
        Label nameLabel = new Label(name);
        Label sizeLabel = new Label(size);
        Label timeLabel = new Label(time);

        nameLabel.setPrefWidth(280);
        nameLabel.setMinWidth(100);
        sizeLabel.setPrefWidth(80);
        sizeLabel.setMinWidth(60);
        timeLabel.setPrefWidth(80);
        timeLabel.setMinWidth(60);

        sizeLabel.setAlignment(Pos.CENTER_RIGHT);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        if (isHeader) {
            String headerStyle = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888;";
            nameLabel.setStyle(headerStyle);
            sizeLabel.setStyle(headerStyle);
            timeLabel.setStyle(headerStyle);
        } else {
            nameLabel.setStyle("-fx-font-size: 12px;");
            sizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
            timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        }

        HBox row = new HBox(8, nameLabel, sizeLabel, timeLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));
        return row;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
