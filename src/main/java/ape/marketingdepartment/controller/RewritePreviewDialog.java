package ape.marketingdepartment.controller;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Dialog for previewing AI rewrite results before applying them.
 */
public class RewritePreviewDialog {

    private final String originalContent;
    private final String rewrittenContent;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public RewritePreviewDialog(String originalContent, String rewrittenContent) {
        this.originalContent = originalContent;
        this.rewrittenContent = rewrittenContent;
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
    }

    /**
     * Show the dialog and return true if user confirms the rewrite.
     */
    public boolean showAndWait(Stage owner) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Confirm Rewrite");
        dialog.setHeaderText("Review the AI-generated rewrite before applying");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setResizable(true);

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefWidth(900);
        content.setPrefHeight(600);

        // Side by side comparison
        HBox comparison = new HBox(10);
        comparison.setPrefHeight(500);
        VBox.setVgrow(comparison, Priority.ALWAYS);

        // Original content pane
        VBox originalPane = new VBox(5);
        HBox.setHgrow(originalPane, Priority.ALWAYS);
        Label originalLabel = new Label("Original Content");
        originalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
        WebView originalView = new WebView();
        originalView.getEngine().loadContent(renderMarkdown(originalContent, "#f5f5f5"));
        VBox.setVgrow(originalView, Priority.ALWAYS);
        originalPane.getChildren().addAll(originalLabel, originalView);

        // Rewritten content pane
        VBox rewrittenPane = new VBox(5);
        HBox.setHgrow(rewrittenPane, Priority.ALWAYS);
        Label rewrittenLabel = new Label("AI Rewritten Content");
        rewrittenLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");
        WebView rewrittenView = new WebView();
        rewrittenView.getEngine().loadContent(renderMarkdown(rewrittenContent, "#f0fff0"));
        VBox.setVgrow(rewrittenView, Priority.ALWAYS);
        rewrittenPane.getChildren().addAll(rewrittenLabel, rewrittenView);

        comparison.getChildren().addAll(originalPane, rewrittenPane);

        // Info label
        Label infoLabel = new Label("Click 'Apply Rewrite' to replace the original content with the rewritten version.");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        content.getChildren().addAll(comparison, infoLabel);

        dialog.getDialogPane().setContent(content);

        // Buttons
        ButtonType applyButton = new ButtonType("Apply Rewrite", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, cancelButton);

        // Style the apply button
        Button applyBtn = (Button) dialog.getDialogPane().lookupButton(applyButton);
        applyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");

        dialog.setResultConverter(buttonType -> buttonType == applyButton);

        Optional<Boolean> result = dialog.showAndWait();
        return result.orElse(false);
    }

    private String renderMarkdown(String markdown, String bgColor) {
        Node document = markdownParser.parse(markdown);
        String html = htmlRenderer.render(document);

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        padding: 15px;
                        line-height: 1.6;
                        color: #333;
                        font-size: 13px;
                        background: %s;
                        margin: 0;
                    }
                    h1 { font-size: 20px; color: #2c3e50; margin-top: 0; }
                    h2 { font-size: 16px; color: #34495e; }
                    h3 { font-size: 14px; color: #555; }
                    p { margin: 10px 0; }
                    code {
                        background: #e8e8e8;
                        padding: 2px 5px;
                        border-radius: 3px;
                        font-family: 'Consolas', 'Monaco', monospace;
                    }
                    pre {
                        background: #e8e8e8;
                        padding: 10px;
                        border-radius: 5px;
                        overflow-x: auto;
                    }
                    blockquote {
                        border-left: 4px solid #3498db;
                        margin: 10px 0;
                        padding-left: 15px;
                        color: #666;
                    }
                    ul, ol { padding-left: 25px; }
                    li { margin: 5px 0; }
                </style>
            </head>
            <body>
                %s
            </body>
            </html>
            """.formatted(bgColor, html);
    }
}
