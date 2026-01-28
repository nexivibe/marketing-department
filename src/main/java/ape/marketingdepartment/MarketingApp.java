package ape.marketingdepartment;

import ape.marketingdepartment.controller.ProjectController;
import ape.marketingdepartment.controller.StartupController;
import ape.marketingdepartment.model.AppSettings;
import ape.marketingdepartment.model.Project;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MarketingApp extends Application {
    private static final double MIN_WIDTH = 800;
    private static final double MIN_HEIGHT = 600;

    private Stage primaryStage;
    private AppSettings settings;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        this.settings = AppSettings.load();

        stage.setTitle("Marketing Department");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        showStartupView();
        stage.show();
    }

    public void showStartupView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("startup-view.fxml"));
        Parent root = loader.load();

        StartupController controller = loader.getController();
        controller.initialize(this, settings);

        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Marketing Department");
    }

    public void showProjectView(Project project) throws IOException {
        settings.addRecentProject(project.getPath().toString());
        settings.save();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("project-view.fxml"));
        Parent root = loader.load();

        ProjectController controller = loader.getController();
        controller.initialize(this, project);

        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Marketing Department - " + project.getTitle());
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch();
    }
}
