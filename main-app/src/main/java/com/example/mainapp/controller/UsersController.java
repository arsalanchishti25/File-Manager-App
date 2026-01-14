package com.example.mainapp.controller;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.MysqlEventLogger;
import com.example.mainapp.model.User;
import com.example.mainapp.service.*;
import com.example.mainapp.util.ViewNavigator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UsersController {

    @FXML
    private TableView<User> usersTable;
    @FXML
    private TableColumn<User, String> usernameColumn;
    @FXML
    private TableColumn<User, String> hashedPasswordColumn;
    @FXML
    private TableColumn<User, String> roleColumn;
    @FXML
    private TableColumn<User, String> createdColumn;
    @FXML
    private Label statusLabel;

    private final ObservableList<User> userData = FXCollections.observableArrayList();
    private final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
        initializeUserTable();
        loadAllUsers();
    }

    private void initializeUserTable() {
        usernameColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("username"));
        hashedPasswordColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("passwordHash"));

        roleColumn.setCellValueFactory(cellData ->
                Bindings.createStringBinding(
                        () -> cellData.getValue().getRole().toString()
                )
        );

        createdColumn.setCellValueFactory(cellData ->
                Bindings.createStringBinding(
                        () -> cellData.getValue().getCreatedAt().format(dateTimeFormatter)
                )
        );

        usersTable.setItems(userData);
    }

    private void loadAllUsers() {
        userData.clear();
        List<User> users;
        if (connectivityService.isOnline()) {
            // live from MySQL
            users = new UserManagementService().getAllUsers();
        } else {
            // offline: best-effort from cached_users
            // you could expose a SyncRepository.findAllCachedUsers() if desired
            users = new java.util.ArrayList<>();
        }
        userData.addAll(users);
        if (!connectivityService.isOnline()) {
            statusLabel.setText("Offline: user list may be partial.");
            statusLabel.setStyle("-fx-text-fill: orange;");
        } else {
            statusLabel.setText("");
        }
    }

    @FXML
    private void onAddUserClicked(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event,
                    "register-view.fxml",
                    400, 450,
                    "File Manager - Add User (Admin)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onPromoteDemoteClicked(ActionEvent event){
        User selected = usersTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            statusLabel.setText("Select a record first.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        if (currentUser != null && selected.getId() == currentUser.getId()){
            statusLabel.setText("You cannot demote currently logged in user.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        String oldRole = selected.getRole().name();
        String newRole = oldRole.equals("STANDARD") ? "ADMIN" : "STANDARD";

        try{
            userService.promoteDemoteUser(selected.getId());
            loadAllUsers();
            if (currentUser != null) {
                eventLogger.logUserRoleChanged(currentUser.getId(), selected.getId(), selected.getUsername(),
                        oldRole, newRole);
            }
            statusLabel.setText(connectivityService.isOnline()
                    ? "User role updated successfully."
                    : "User role updated locally and will sync when online.");
            statusLabel.setStyle("-fx-text-fill: green;");
        } catch(IllegalArgumentException ex) {
            statusLabel.setText("Failed to update user role.");
            statusLabel.setStyle("-fx-text-fill: red;");
            System.err.println("Promote/Demote failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onDeleteUserClicked(ActionEvent event) {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        if (currentUser != null && selected.getId() == currentUser.getId()){
            statusLabel.setText("You cannot delete currently logged in user.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            long userId = selected.getId();
            String userName = selected.getUsername();
            userService.deleteUser(userId);
            userData.remove(selected);
            if (currentUser != null) {
                eventLogger.logUserDeleted(currentUser.getId(), userId, userName);
            }
            statusLabel.setText(connectivityService.isOnline()
                    ? "User deleted successfully."
                    : "User deleted locally and will sync when online.");
            statusLabel.setStyle("-fx-text-fill: green;");
        } catch (IllegalArgumentException ex) {
            System.err.println("Delete failed: " + ex.getMessage());
            statusLabel.setText("Failed to delete user.");
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void onBackClicked(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "main-view.fxml", 800, 600, "File Manager - Main");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
