package com.example.mainapp.controller;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.MysqlEventLogger;
import com.example.mainapp.model.User;
import com.example.mainapp.service.*;
import com.example.mainapp.util.ViewNavigator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;

public class RegisterController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmField;
    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<User.Role> roleComboBox;
    @FXML
    private Button backButton;

    private boolean returnToUserList = false;
    private boolean isAdminContext = false;

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
    private void initialize() {
        SessionManager.getInstance().getCurrentUser().ifPresentOrElse(
                user -> {
                    boolean isAdmin = user.isAdmin();
                    isAdminContext = isAdmin;

                    roleComboBox.setVisible(isAdmin);
                    roleComboBox.setManaged(isAdmin);

                    if (isAdmin) {
                        roleComboBox.getItems().setAll(User.Role.STANDARD, User.Role.ADMIN);
                        roleComboBox.setValue(User.Role.STANDARD);
                    }

                    returnToUserList = isAdmin;
                    backButton.setText(isAdmin ? "Back to User List" : "Back to Login");
                },
                () -> {
                    isAdminContext = false;
                    roleComboBox.setVisible(false);
                    roleComboBox.setManaged(false);
                    returnToUserList = false;
                    backButton.setText("Back to Login");
                }
        );
    }

    @FXML
    private void onRegisterClicked(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirm = confirmField.getText();

        if (username == null || username.isBlank()) {
            showError("Please enter a username.");
            return;
        }
        if (password == null || password.isBlank()) {
            showError("Please enter a password.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            User.Role role = isAdminContext ? roleComboBox.getValue() : User.Role.STANDARD;
            Long actorId = SessionManager.getInstance()
                    .getCurrentUser()
                    .map(User::getId)
                    .orElse(null);

            User created = userService.register(username, password, role, actorId);

            if (returnToUserList) {
                showSuccess("User created. Returning to user list...");
                SessionManager.getInstance().getCurrentUser().ifPresent(admin ->
                        eventLogger.logUserRegistered(
                                admin.getId(),
                                created.getId(),
                                created.getUsername())
                );
                goToUsersView(event);
            } else {
                showSuccess("Registration successful! Returning to login...");
                eventLogger.logUserRegistered(
                        created.getId(),
                        created.getId(),
                        created.getUsername());
                switchToLogin(event);
            }

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onBackClicked(ActionEvent event) {
        if (returnToUserList) {
            goToUsersView(event);
        } else {
            switchToLogin(event);
        }
    }

    private void goToUsersView(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "users-view.fxml", 800, 600, "File Manager - User Management");
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to return to user list.");
        }
    }

    private void switchToLogin(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "login-view.fxml", 400, 300, "File Manager - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void showSuccess(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: green;");
    }
}
