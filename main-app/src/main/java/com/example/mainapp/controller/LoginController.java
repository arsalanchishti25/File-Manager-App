package com.example.mainapp.controller;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.MysqlEventLogger;
import com.example.mainapp.model.User;
import com.example.mainapp.service.*;
import com.example.mainapp.util.ViewNavigator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.util.Optional;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    // CHANGE 1: Make services non-final so we can initialize them in initialize()
    private ConnectivityService connectivityService;
    private SyncAwareUserService userService;
    private SyncService syncService;

    private final MysqlEventLogger eventLogger =
            new MysqlEventLogger(new RemoteMySQLDataSource());

    // CHANGE 2: Add initialize method to set up services
    @FXML
    public void initialize() {
        try {
            // Get MQTT config
            String brokerUrl = System.getenv("MQTT_BROKER_URL");
            if (brokerUrl == null) brokerUrl = "tcp://filemanager-mqtt:1883";
            
            String mainAppId = "main-app-" + System.currentTimeMillis();

            // Initialize connectivity service
            this.connectivityService = new ConnectivityService();

            // Initialize user service
            this.userService = new SyncAwareUserService(
                connectivityService,
                new AuthService(),
                new RegistrationService(),
                new UserManagementService(),
                new PermissionService(),
                new com.example.mainapp.repository.SyncRepository()
            );

            // CHANGE 3: Initialize SyncService with MQTT parameters
            this.syncService = new SyncService(brokerUrl, mainAppId);

        } catch (Exception e) {
            System.err.println("Failed to initialize services: " + e.getMessage());
            e.printStackTrace();
            showError("Initialization Error", "Failed to connect to services: " + e.getMessage());
        }
    }

    @FXML
    private void onLoginClicked(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.isBlank() ||
            password == null || password.isBlank()) {
            statusLabel.setText("Please enter username and password.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        Optional<User> userOpt = userService.login(username, password);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            SessionManager.getInstance().login(user);

            eventLogger.logLoginSuccess(user.getId(), user.getUsername());
            statusLabel.setText("Login successful!");
            statusLabel.setStyle("-fx-text-fill: green;");

            // CHANGE 4: Run sync if online (syncService is now properly initialized)
            if (connectivityService.isOnline()) {
                try {
                    System.out.println("[LoginController] Running sync after successful login...");
                    syncService.runSync();
                    System.out.println("[LoginController] Sync completed successfully");
                } catch (Exception e) {
                    System.err.println("[LoginController] Sync failed: " + e.getMessage());
                    // Don't block login if sync fails
                }
            } else {
                System.out.println("[LoginController] Offline mode - skipping sync");
            }

            switchToMainScene(event, user);
        } else {
            eventLogger.logLoginFailure(username, "Invalid credentials or offline user not cached.");
            statusLabel.setText("Invalid credentials.");
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void switchToMainScene(ActionEvent event, User user) {
        try {
            ViewNavigator.switchScene(event, "main-view.fxml", 800, 600, "File Manager - Main");
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Failed to load main view.");
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void onRegisterClicked(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "register-view.fxml", 400, 400, "File Manager - Register");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onExitClicked(ActionEvent event) {
        // CHANGE 5: Cleanup services before exit
        cleanupServices();
        ViewNavigator.closeWindow(event);
    }

    // CHANGE 6: Add cleanup method
    private void cleanupServices() {
        if (syncService != null) {
            // SyncService should delegate to its internal services
            // For now, we'll handle it in the service itself
            syncService.shutdown();
        }
    }

    // CHANGE 7: Add error dialog helper
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
