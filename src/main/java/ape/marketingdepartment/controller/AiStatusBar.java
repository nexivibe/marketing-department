package ape.marketingdepartment.controller;

import ape.marketingdepartment.service.ai.AiStatus;
import ape.marketingdepartment.service.ai.AiStatusListener;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * A status bar component that displays AI operation status.
 */
public class AiStatusBar extends HBox implements AiStatusListener {

    private final Circle statusIndicator;
    private final ProgressIndicator progressIndicator;
    private final Label statusLabel;
    private final Label modelLabel;

    private AiStatus currentStatus = AiStatus.idle();
    private Timeline updateTimeline;

    public AiStatusBar() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setPadding(new Insets(6, 12, 6, 12));
        setMinHeight(28);
        setStyle("-fx-background-color: linear-gradient(to bottom, #f8f8f8, #e8e8e8); " +
                 "-fx-border-color: #ccc; -fx-border-width: 1 0 0 0;");

        // Make this fill parent width
        HBox.setHgrow(this, Priority.ALWAYS);
        setMaxWidth(Double.MAX_VALUE);

        // Status indicator dot
        statusIndicator = new Circle(6);
        statusIndicator.setFill(Color.GRAY);
        statusIndicator.setStroke(Color.web("#999"));
        statusIndicator.setStrokeWidth(1);

        // Progress spinner (hidden when idle)
        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(18, 18);
        progressIndicator.setMaxSize(18, 18);
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);

        // AI label
        Label aiLabel = new Label("AI:");
        aiLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #444; -fx-font-size: 12px;");

        // Model label
        modelLabel = new Label("");
        modelLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-weight: bold;");
        modelLabel.setVisible(false);
        modelLabel.setManaged(false);

        // Status text
        statusLabel = new Label("Ready - no AI activity");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        getChildren().addAll(statusIndicator, progressIndicator, aiLabel, modelLabel, statusLabel);

        // Timer to update elapsed time display
        updateTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> updateElapsedTime()));
        updateTimeline.setCycleCount(Animation.INDEFINITE);
    }

    @Override
    public void onStatusChanged(AiStatus status) {
        Platform.runLater(() -> updateStatus(status));
    }

    private void updateStatus(AiStatus status) {
        this.currentStatus = status;

        // Update model label
        if (status.model() != null && !status.model().isEmpty()) {
            modelLabel.setText("[" + status.model() + "]");
            modelLabel.setVisible(true);
            modelLabel.setManaged(true);
        } else {
            modelLabel.setVisible(false);
            modelLabel.setManaged(false);
        }

        // Update status text and indicator based on state
        switch (status.state()) {
            case IDLE -> {
                statusIndicator.setFill(Color.GRAY);
                statusIndicator.setStroke(Color.web("#999"));
                statusLabel.setText("Ready - no AI activity");
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
                progressIndicator.setVisible(false);
                progressIndicator.setManaged(false);
                statusIndicator.setVisible(true);
                stopTimer();
            }
            case CONNECTING, SENDING -> {
                statusIndicator.setFill(Color.ORANGE);
                statusIndicator.setStroke(Color.web("#cc8800"));
                statusLabel.setText(status.message());
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #b8860b; -fx-font-weight: bold;");
                progressIndicator.setVisible(true);
                progressIndicator.setManaged(true);
                statusIndicator.setVisible(false);
                startTimer();
            }
            case WAITING, RECEIVING -> {
                statusIndicator.setFill(Color.DODGERBLUE);
                statusIndicator.setStroke(Color.web("#0066cc"));
                statusLabel.setText(status.message());
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e90ff; -fx-font-weight: bold;");
                progressIndicator.setVisible(true);
                progressIndicator.setManaged(true);
                statusIndicator.setVisible(false);
                startTimer();
            }
            case PROCESSING -> {
                statusIndicator.setFill(Color.PURPLE);
                statusIndicator.setStroke(Color.web("#660066"));
                statusLabel.setText(status.message());
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #800080; -fx-font-weight: bold;");
                progressIndicator.setVisible(true);
                progressIndicator.setManaged(true);
                statusIndicator.setVisible(false);
                startTimer();
            }
            case COMPLETE -> {
                statusIndicator.setFill(Color.LIMEGREEN);
                statusIndicator.setStroke(Color.web("#228b22"));
                statusLabel.setText(status.message());
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #228b22; -fx-font-weight: bold;");
                progressIndicator.setVisible(false);
                progressIndicator.setManaged(false);
                statusIndicator.setVisible(true);
                stopTimer();
                // Auto-reset to idle after 5 seconds
                scheduleReset();
            }
            case ERROR -> {
                statusIndicator.setFill(Color.RED);
                statusIndicator.setStroke(Color.web("#990000"));
                statusLabel.setText("Error: " + status.message());
                statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc143c; -fx-font-weight: bold;");
                progressIndicator.setVisible(false);
                progressIndicator.setManaged(false);
                statusIndicator.setVisible(true);
                stopTimer();
                // Auto-reset to idle after 10 seconds
                scheduleReset();
            }
        }
    }

    private void updateElapsedTime() {
        if (currentStatus.isActive() && currentStatus.startTime() > 0) {
            long elapsed = System.currentTimeMillis() - currentStatus.startTime();
            String baseMessage = currentStatus.state().getDefaultMessage();
            statusLabel.setText(String.format("%s (%.1fs)", baseMessage, elapsed / 1000.0));
        }
    }

    private void startTimer() {
        if (updateTimeline.getStatus() != Animation.Status.RUNNING) {
            updateTimeline.play();
        }
    }

    private void stopTimer() {
        updateTimeline.stop();
    }

    private void scheduleReset() {
        Timeline resetTimeline = new Timeline(new KeyFrame(
                Duration.seconds(currentStatus.state() == AiStatus.State.ERROR ? 10 : 5),
                e -> {
                    if (currentStatus.state() == AiStatus.State.COMPLETE ||
                        currentStatus.state() == AiStatus.State.ERROR) {
                        updateStatus(AiStatus.idle());
                    }
                }
        ));
        resetTimeline.play();
    }

    /**
     * Manually set the status to idle.
     */
    public void setIdle() {
        updateStatus(AiStatus.idle());
    }

    /**
     * Get the current status.
     */
    public AiStatus getCurrentStatus() {
        return currentStatus;
    }
}
