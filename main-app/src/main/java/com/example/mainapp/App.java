package com.example.mainapp;

import com.example.mainapp.db.DatabaseInitializer;
import com.example.mainapp.util.ViewNavigator;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseInitializer.initializeLocalDatabase();
        // DatabaseInitializer.initializeRemoteDatabase();
        ViewNavigator.switchScene(stage, "login-view.fxml", 400, 300, "File Manager - Login");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
