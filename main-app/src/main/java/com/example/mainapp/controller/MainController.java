package com.example.mainapp.controller;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.MysqlEventLogger;
import com.example.mainapp.model.File;
import com.example.mainapp.model.User;
import com.example.mainapp.repository.MySQLUserRepository;
import com.example.mainapp.repository.SyncRepository;
import com.example.mainapp.repository.UserRepository;
import com.example.mainapp.service.*;
import com.example.mainapp.util.ViewNavigator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML
    private Label usernameLabel;

    @FXML
    private TableView<File> fileTable;

    @FXML
    private TableColumn<File, String> nameColumn;

    @FXML
    private TableColumn<File, String> sizeColumn;

    @FXML
    private TableColumn<File, String> createdColumn;

    @FXML
    private TableColumn<File, String> modifiedColumn;

    @FXML
    private TableColumn<File, String> ownerColumn;

    @FXML
    private Button manageUsersBtn;

    @FXML
    private Button viewEventLogsBtn;

    private final ObservableList<File> fileData = FXCollections.observableArrayList();

    private User currentUser;

    private final MysqlEventLogger eventLogger =
            new MysqlEventLogger(new RemoteMySQLDataSource());

    private final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository =
            new MySQLUserRepository(new RemoteMySQLDataSource());

    // CHANGE 1: Make these non-final so we can initialize them in initialize()
    private ConnectivityService connectivityService;
    private SyncRepository syncRepository;
    private SyncAwareFileService fileService;
    private SyncAwareUserService userService;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // CHANGE 2: Initialize services with MQTT parameters
        try {
            String brokerUrl = System.getenv("MQTT_BROKER_URL");
            if (brokerUrl == null) brokerUrl = "tcp://filemanager-mqtt:1883";
            
            String mainAppId = "main-app-" + System.currentTimeMillis();

            // Initialize connectivity and sync repository
            this.connectivityService = new ConnectivityService();
            this.syncRepository = new SyncRepository();

            // Initialize file service with MQTT
            this.fileService = new SyncAwareFileService(brokerUrl, mainAppId);

            // Initialize user service
            this.userService = new SyncAwareUserService(
                connectivityService,
                new AuthService(),
                new RegistrationService(),
                new UserManagementService(),
                new PermissionService(),
                syncRepository
            );

        } catch (Exception e) {
            System.err.println("Failed to initialize services: " + e.getMessage());
            e.printStackTrace();
            showError("Initialization Error", "Failed to connect to services: " + e.getMessage());
            return;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        sessionManager.getCurrentUser().ifPresentOrElse(
                user -> {
                    this.currentUser = user;

                    usernameLabel.setText(
                            "User: " + user.getUsername() +
                                    (user.isAdmin() ? " [ADMIN]" : " [Standard]"));
                    usernameLabel.setStyle(
                            user.isAdmin()
                                    ? "-fx-text-fill: #ff0000;"
                                    : "-fx-text-fill: #333333;");
                    manageUsersBtn.setVisible(user.isAdmin());
                    viewEventLogsBtn.setVisible(user.isAdmin());

                    if (!connectivityService.isOnline()) {
                        usernameLabel.setText(usernameLabel.getText() + " [OFFLINE]");
                    }

                    initializeFileTable();
                    loadFilesForCurrentUser();
                },
                () -> {
                    SessionManager.getInstance().logout();
                    redirectToLogin();
                });
    }

    private void redirectToLogin() {
        try {
            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            ViewNavigator.switchScene(stage, "login-view.fxml", 400, 300, "File Manager - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String resolveOwnerName(long ownerId) {
        Optional<User> userOpt = userRepository.findById(ownerId);
        return userOpt.map(User::getUsername).orElse("Unknown");
    }

    private void initializeFileTable() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("filename"));

        ownerColumn.setCellValueFactory(cellData -> Bindings.createStringBinding(
                () -> resolveOwnerName(cellData.getValue().getOwnerId())));

        sizeColumn.setCellValueFactory(cellData -> Bindings.createStringBinding(
                () -> cellData.getValue().getFormattedSize()));
        createdColumn.setCellValueFactory(cellData -> Bindings.createStringBinding(
                () -> cellData.getValue().getCreatedAt().format(dateTimeFormatter)));
        modifiedColumn.setCellValueFactory(cellData -> Bindings.createStringBinding(
                () -> cellData.getValue().getLastModified().format(dateTimeFormatter)));

        fileTable.setItems(fileData);
    }

    private void loadFilesForCurrentUser() {
        fileData.clear();
        if (currentUser == null) return;

        if (currentUser.isAdmin()) {
            fileData.addAll(fileService.getUserFiles(currentUser.getId(), true));
        } else {
            fileData.addAll(fileService.getUserFiles(currentUser.getId(), false));
        }
    }

    @FXML
    private void onAddFileClicked(ActionEvent event) {
        if (currentUser == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select File to Add");
        java.io.File chosen = chooser.showOpenDialog(
                ((Stage) fileTable.getScene().getWindow()));
        if (chosen == null) {
            return;
        }

        // Show progress indicator
        ProgressIndicator progress = new ProgressIndicator();
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Uploading...");
        progressAlert.setHeaderText("Uploading " + chosen.getName());
        progressAlert.setGraphic(progress);
        progressAlert.getButtonTypes().clear();
        progressAlert.show();

        // Run upload in background thread
        new Thread(() -> {
            try {
                File newFile = fileService.uploadFile(currentUser.getId(), chosen.toPath());
                
                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();
                    fileData.add(newFile);
                    fileTable.refresh();
                    eventLogger.logFileUploaded(
                        currentUser.getId(), 
                        newFile.getId(), 
                        newFile.getFilename(),
                        newFile.getSizeInBytes()
                    );
                    showInfo("Upload Complete", "File uploaded: " + newFile.getFilename());
                });
                
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();
                    System.err.println("Upload failed: " + e.getMessage());
                    showError("Upload Failed", "Failed to upload file: " + e.getMessage());
                });
            }
        }).start();        
        // try {
        //     File newFile = fileService.uploadFile(currentUser.getId(), chosen.toPath());
        //     fileData.add(newFile);
        //     fileTable.refresh();
        //     eventLogger.logFileUploaded(currentUser.getId(), newFile.getId(), newFile.getFilename(),
        //             newFile.getSizeInBytes());
        // } catch (Exception e) {
        //     System.err.println("Upload failed: " + e.getMessage());
        //     showError("Upload Failed", "Failed to upload file: " + e.getMessage());
        // }
    }

    @FXML
    private void onEditContentClicked(ActionEvent event) {
        File selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentUser == null) return;

        try {
            Path file = fileService.downloadFile(selected.getId(), currentUser.getId(), currentUser.isAdmin());
            // java.io.File f = file.toFile();
            eventLogger.logFileEdited(
                    currentUser.getId(),
                    selected.getId(),
                    selected.getFilename()
            );
            System.out.println("[MainController] File downloaded to: " + file.toAbsolutePath());
            // System.out.println("f: " + f);
            openInNano(file);
            // Step 3: Log the edit event
            eventLogger.logFileEdited(
                currentUser.getId(),
                selected.getId(),
                selected.getFilename()
            );
            System.out.println("[MainController] File opened in nano editor");
        } catch (IOException e) {
            System.err.println("Failed to edit " + selected.getFilename()
                    + ". Error message: " + e.getMessage());
            showError("Edit Failed", "Failed to download file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during edit: " + e.getMessage());
            showError("Edit Failed", "Unexpected error: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteFileClicked(ActionEvent event) {
        File selected = fileTable.getSelectionModel().getSelectedItem();

        if (selected == null || currentUser == null) {
            return;
        }

        long fileId = selected.getId();
        String fileName = selected.getFilename();
        try {
            fileService.deleteFile(
                    selected.getId(),
                    currentUser.getId(),
                    currentUser.isAdmin());
            fileData.remove(selected);
            eventLogger.logFileDeleted(currentUser.getId(), fileId, fileName);
        } catch (Exception ex) {  // CHANGE 3: Catch Exception (not just IllegalArgumentException)
            System.err.println("Delete failed: " + ex.getMessage());
            showError("Delete Failed", "Failed to delete file: " + ex.getMessage());
        }
    }

    @FXML
    private void onRenameFileClicked(ActionEvent event) {
        File selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentUser == null) {
            return;
        }
        String fileOldName = selected.getFilename();

        boolean canWrite = userService.hasWriteAccess(
                currentUser.getId(),
                selected.getId(),
                currentUser.isAdmin(),
                selected.getOwnerId());

        if (!canWrite) {
            System.err.println("Rename permission denied");
            showError("Permission Denied", "You don't have permission to rename this file.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.getFilename());
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Rename File");
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(newName -> {
            try {
                File updated = fileService.renameFile(
                        selected.getId(),
                        currentUser.getId(),
                        currentUser.isAdmin(),
                        newName);

                int index = fileData.indexOf(selected);
                if (index >= 0) {
                    fileData.set(index, updated);
                }
                fileTable.getSelectionModel().select(updated);
                fileTable.refresh();
                eventLogger.logFileRenamed(currentUser.getId(), updated.getId(),
                        fileOldName, updated.getFilename());
            } catch (IllegalArgumentException ex) {
                System.err.println("Rename failed: " + ex.getMessage());
                showError("Rename Failed", ex.getMessage());
            }
        });
    }

    @FXML
    private void onShareSelectedClicked(ActionEvent event) {
        File selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || currentUser == null) {
            return;
        }

        if (!currentUser.isAdmin() && selected.getOwnerId() != currentUser.getId()) {
            System.err.println("You do not have permission to share this file.");
            showError("Permission Denied", "You do not have permission to share this file.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Share File");
        dialog.setHeaderText("Share \"" + selected.getFilename() + "\" with another user");

        ButtonType shareButtonType = new ButtonType("Share", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(shareButtonType, ButtonType.CANCEL);

        ComboBox<User> userCombo = new ComboBox<>();
        ComboBox<String> permCombo = new ComboBox<>();

        List<User> allUsers = userRepository.findAll();
        List<User> standardUsers = allUsers.stream()
                .filter(u -> u.getRole() == User.Role.STANDARD)
                .filter(u -> u.getId() != currentUser.getId())
                .toList();

        userCombo.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return (user == null) ? "" : user.getUsername();
            }

            @Override
            public User fromString(String s) {
                return null;
            }
        });
        userCombo.getItems().addAll(standardUsers);
        userCombo.setPromptText("Select user");

        permCombo.getItems().addAll("READ", "READ_WRITE");
        permCombo.setValue("READ");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("User:"), 0, 0);
        grid.add(userCombo, 1, 0);
        grid.add(new Label("Permission:"), 0, 1);
        grid.add(permCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Node shareButton = dialog.getDialogPane().lookupButton(shareButtonType);
        shareButton.setDisable(true);

        userCombo.valueProperty().addListener((obs, oldV, newV) -> {
            shareButton.setDisable(newV == null);
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == shareButtonType) {
                User targetUser = userCombo.getValue();
                String perm = permCombo.getValue();

                if (targetUser != null && perm != null) {
                    userService.shareFile(selected.getId(), targetUser.getId(), perm);
                    eventLogger.logFileShared(
                            currentUser.getId(),
                            selected.getId(),
                            selected.getFilename(),
                            targetUser.getId(),
                            targetUser.getUsername(),
                            perm
                    );
                    System.out.println("Shared file " + selected.getFilename() +
                            " with " + targetUser.getUsername() +
                            " (" + perm + ")");
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    @FXML
    private void onEventLogsClicked(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "eventlogs-view.fxml", 800, 600, "File Manager - Event Logs");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onManageUsersClicked(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "users-view.fxml", 800, 600, "File Manager - User Management");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onUpdatePasswordClicked(ActionEvent event) {
        Stage owner = (Stage) ((Node) event.getSource()).getScene().getWindow();
        try {
            ViewNavigator.openModal(owner,
                    "password-view.fxml",
                    400, 250,
                    "Update Password");
        } catch (IOException e) {
            System.err.println("Error loading update password view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void onLogoutClicked(ActionEvent event) {
        // CHANGE 4: Cleanup services on logout
        if (fileService != null) {
            fileService.shutdown();
        }
        
        SessionManager.getInstance().logout();
        try {
            ViewNavigator.switchScene(event, "login-view.fxml", 400, 300, "File Manager - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Open a file in nano editor.
     * This will launch nano in a new terminal window.
     */
    private void openInNano(Path filePath) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        
        ProcessBuilder processBuilder;
        
        if (os.contains("linux")) {
            // Option 1: Open nano in current terminal (blocks JavaFX app)
            // processBuilder = new ProcessBuilder("nano", filePath.toAbsolutePath().toString());
            
            // Option 2: Open nano in new gnome-terminal window (recommended)
            processBuilder = new ProcessBuilder(
                "gnome-terminal",
                "--",
                "nano",
                filePath.toAbsolutePath().toString()
            );
            
            // Alternative for other terminal emulators:
            // xterm: new ProcessBuilder("xterm", "-e", "nano", filePath.toAbsolutePath().toString());
            // konsole: new ProcessBuilder("konsole", "-e", "nano", filePath.toAbsolutePath().toString());
            // xfce4-terminal: new ProcessBuilder("xfce4-terminal", "-e", "nano " + filePath.toAbsolutePath().toString());
            
        } else if (os.contains("mac")) {
            // macOS: Open nano in Terminal.app
            processBuilder = new ProcessBuilder(
                "open",
                "-a",
                "Terminal",
                filePath.toAbsolutePath().toString()
            );
            
        } else if (os.contains("win")) {
            // Windows: Use notepad as fallback (nano not available by default)
            processBuilder = new ProcessBuilder(
                "notepad.exe",
                filePath.toAbsolutePath().toString()
            );
            
        } else {
            throw new IOException("Unsupported operating system: " + os);
        }
        
        processBuilder.inheritIO();  // Inherit I/O so you can see nano output
        // Process process = processBuilder.start();
        
        // Optional: Wait for nano to close and re-upload the file
        // Uncomment if you want to re-upload after editing
        /*
        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                System.out.println("[MainController] Nano closed with exit code: " + exitCode);
                
                // Ask user if they want to re-upload the modified file
                javafx.application.Platform.runLater(() -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Re-upload File?");
                    confirm.setHeaderText("File has been edited");
                    confirm.setContentText("Do you want to upload the modified file?");
                    
                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            try {
                                // Re-upload the edited file
                                File updatedFile = fileService.uploadFile(
                                    currentUser.getId(), 
                                    filePath
                                );
                                
                                // Update table
                                int index = fileData.indexOf(selected);
                                if (index >= 0) {
                                    fileData.set(index, updatedFile);
                                    fileTable.refresh();
                                }
                                
                                showInfo("Upload Complete", "Modified file uploaded successfully");
                                
                            } catch (Exception e) {
                                showError("Upload Failed", "Failed to upload modified file: " + e.getMessage());
                            }
                        }
                    });
                });
                
            } catch (InterruptedException e) {
                System.err.println("Error waiting for nano: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }).start();
        */
    }

    /**
     * Open a file in nano editor with automatic terminal detection.
     */
    // private void openInNano(Path filePath) throws IOException {
    //     String os = System.getProperty("os.name").toLowerCase();        
    //     if (os.contains("linux")) {
    //         // Try common terminal emulators in order of preference
    //         String[] terminals = {
    //             "gnome-terminal",
    //             "konsole",
    //             "xfce4-terminal",
    //             "xterm",
    //             "terminator",
    //             "tilix"
    //         };
    //         for (String terminal : terminals) {
    //             if (isCommandAvailable(terminal)) {
    //                 ProcessBuilder processBuilder = buildTerminalCommand(terminal, filePath);
    //                 processBuilder.inheritIO();
    //                 processBuilder.start();
    //                 System.out.println("[MainController] Opened nano in " + terminal);
    //                 return;
    //             }
    //         }
    //         throw new IOException("No supported terminal emulator found. Please install gnome-terminal, konsole, or xterm.");
    //     } else if (os.contains("mac")) {
    //         // macOS
    //         ProcessBuilder processBuilder = new ProcessBuilder(
    //             "open",
    //             "-a",
    //             "Terminal",
    //             filePath.toAbsolutePath().toString()
    //         );
    //         processBuilder.start();
    //     } else if (os.contains("win")) {
    //         // Windows fallback to notepad
    //         ProcessBuilder processBuilder = new ProcessBuilder(
    //             "notepad.exe",
    //             filePath.toAbsolutePath().toString()
    //         );
    //         processBuilder.start();
    //     } else {
    //         throw new IOException("Unsupported operating system: " + os);
    //     }
    // }

    /**
     * Check if a command is available in the system PATH.
     */
    // private boolean isCommandAvailable(String command) {
    //     try {
    //         Process process = new ProcessBuilder("which", command).start();
    //         int exitCode = process.waitFor();
    //         return exitCode == 0;
    //     } catch (Exception e) {
    //         return false;
    //     }
    // }

    /**
     * Build terminal command based on terminal emulator type.
     */
    // private ProcessBuilder buildTerminalCommand(String terminal, Path filePath) {
    //     String absolutePath = filePath.toAbsolutePath().toString();    
    //     return switch (terminal) {
    //         case "gnome-terminal" -> new ProcessBuilder("gnome-terminal", "--", "nano", absolutePath);
    //         case "konsole" -> new ProcessBuilder("konsole", "-e", "nano", absolutePath);
    //         case "xfce4-terminal" -> new ProcessBuilder("xfce4-terminal", "-e", "nano " + absolutePath);
    //         case "xterm" -> new ProcessBuilder("xterm", "-e", "nano", absolutePath);
    //         case "terminator" -> new ProcessBuilder("terminator", "-e", "nano " + absolutePath);
    //         case "tilix" -> new ProcessBuilder("tilix", "-e", "nano " + absolutePath);
    //         default -> new ProcessBuilder("xterm", "-e", "nano", absolutePath);
    //     };
    // }

    // CHANGE 5: Add helper method for error dialogs
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
