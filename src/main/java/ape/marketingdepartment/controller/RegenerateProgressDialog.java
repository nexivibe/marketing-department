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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * A programmatic dialog showing progress during web regeneration.
 * Shows a spinner, elapsed time, status text, and a dismiss button.
 */
public class RegenerateProgressDialog {

    private final Stage stage;
    private final Label timerLabel;
    private final Label statusLabel;
    private final Label resultLabel;
    private final Button dismissButton;
    private Timeline timer;
    private long startTime;

    public RegenerateProgressDialog(Window owner) {
        stage = new Stage();
        stage.setTitle("Regenerate Web Export");
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setResizable(false);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);

        timerLabel = new Label("0.0s");
        timerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        HBox topRow = new HBox(10, spinner, timerLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Starting...");
        statusLabel.setStyle("-fx-font-size: 13px;");
        statusLabel.setWrapText(true);

        resultLabel = new Label();
        resultLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        resultLabel.setWrapText(true);
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);

        dismissButton = new Button("Dismiss");
        dismissButton.setDisable(true);
        dismissButton.setOnAction(e -> {
            stopTimer();
            stage.close();
        });

        VBox root = new VBox(12, topRow, statusLabel, resultLabel, dismissButton);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPrefWidth(400);

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

    public void complete(String resultText) {
        Platform.runLater(() -> {
            stopTimer();
            long elapsedMs = System.currentTimeMillis() - startTime;
            timerLabel.setText(String.format("Completed in %.1fs", elapsedMs / 1000.0));
            statusLabel.setText("Done");
            resultLabel.setText(resultText);
            resultLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            resultLabel.setVisible(true);
            resultLabel.setManaged(true);
            dismissButton.setDisable(false);
        });
    }

    public void error(String errorText) {
        Platform.runLater(() -> {
            stopTimer();
            long elapsedMs = System.currentTimeMillis() - startTime;
            timerLabel.setText(String.format("Failed after %.1fs", elapsedMs / 1000.0));
            statusLabel.setText("Error");
            resultLabel.setText(errorText);
            resultLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");
            resultLabel.setVisible(true);
            resultLabel.setManaged(true);
            dismissButton.setDisable(false);
        });
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
