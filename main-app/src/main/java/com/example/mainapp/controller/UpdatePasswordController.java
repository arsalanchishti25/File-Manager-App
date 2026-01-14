package com.example.mainapp.controller;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.MysqlEventLogger;
import com.example.mainapp.model.User;
import com.example.mainapp.service.*;
// import com.example.filemanager.util.PasswordHasher;
import com.example.mainapp.util.ViewNavigator;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.util.Duration;

import java.util.Optional;

public class UpdatePasswordController {

    @FXML
    private PasswordField currentPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label statusLabel;

    private final SessionManager sessionManager = SessionManager.getInstance();
    private User currentUser;

    private final MysqlEventLogger eventLogger =
            new MysqlEventLogger(new RemoteMySQLDataSource());

    private final ConnectivityService connectivityService = new ConnectivityService();
    private final SyncAwareUserService userService =
            new SyncAwareUserService(
                    connectivityService,
                    new AuthService(),
                    new RegistrationService(),
                    new UserManagementService(),
                    new PermissionService(),
                    new com.example.mainapp.repository.SyncRepository()
            );

    @FXML
    public void initialize() {
        currentUser = sessionManager.getCurrentUser().orElse(null);
    }

    @FXML
    private void handleUpdatePassword(ActionEvent event) {
        statusLabel.setText("");

        if (currentUser == null) {
            showError("No user is logged in");
            return;
        }

        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (!isInputValid(currentPassword, newPassword, confirmPassword)) {
            return;
        }

        // Verify current password using online or offline login
        Optional<User> userOpt = userService.login(currentUser.getUsername(), currentPassword);
        if (userOpt.isEmpty()) {
            showError("Current password is incorrect");
            return;
        }

        try {
            boolean updated = userService.updatePassword(currentUser.getUsername(), newPassword);

            if (updated) {
                showSuccess(connectivityService.isOnline()
                        ? "Password updated successfully!"
                        : "Password updated locally and will sync when online.");
                clearFields();
                eventLogger.logPasswordChanged(currentUser.getId(), currentUser.getUsername());

                PauseTransition delay = new PauseTransition(Duration.seconds(1));
                delay.setOnFinished(e -> ViewNavigator.closeWindow(event));
                delay.play();
            } else {
                showError("Failed to update password");
            }

        } catch (Exception e) {
            showError("Error updating password");
            e.printStackTrace();
        }
    }

    private boolean isInputValid(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            showError("Please enter your current password");
            return false;
        }

        if (newPassword == null || newPassword.isBlank()) {
            showError("Please enter a new password");
            return false;
        }

        if (confirmPassword == null || confirmPassword.isBlank()) {
            showError("Please confirm your new password");
            return false;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("New passwords do not match");
            return false;
        }

        if (currentPassword.equals(newPassword)) {
            showError("New password must be different from current password");
            return false;
        }

        return true;
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        clearFields();
        ViewNavigator.closeWindow(event);
    }

    private void clearFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: green;");
    }
}
