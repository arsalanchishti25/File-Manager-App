package com.example.mainapp.controller;

import java.io.IOException;
import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.model.EventLog;
import com.example.mainapp.repository.EventLogRepository;
import com.example.mainapp.repository.MySQLEventLogRepository;
import com.example.mainapp.service.ConnectivityService;
import com.example.mainapp.service.SessionManager;
import com.example.mainapp.util.ViewNavigator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Label;

public class EventLogsController {

    @FXML
    private TableView<EventLog> logsTable;
    @FXML
    private TableColumn<EventLog, String> timeColumn;
    @FXML
    private TableColumn<EventLog, String> userColumn;
    @FXML
    private TableColumn<EventLog, String> typeColumn;
    @FXML
    private TableColumn<EventLog, String> descriptionColumn;

    private final EventLogRepository eventLogRepository =
            new MySQLEventLogRepository(new RemoteMySQLDataSource());

    private final ObservableList<EventLog> logs = FXCollections.observableArrayList();

    private final SessionManager sessionManager = SessionManager.getInstance();

    @FXML
    private Label statusLabel; //add to FXML near the table

    private final ConnectivityService connectivityService = new ConnectivityService();

    private void loadLogs() {
        if (!connectivityService.isOnline()) {
            logs.clear();
            if (statusLabel != null) {
                statusLabel.setText("Cannot load logs: offline or MySQL unavailable.");
                statusLabel.setStyle("-fx-text-fill: red;");
            }
            return;
        }

        try {
            logs.setAll(eventLogRepository.findRecentLogs(100));
            if (statusLabel != null) {
                statusLabel.setText("");
            }
        } catch (Exception e) {
            logs.clear();
            if (statusLabel != null) {
                statusLabel.setText("Failed to load logs: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        }
    }

    

    @FXML
    private void initialize() {
        sessionManager.getCurrentUser().orElse(null);

        timeColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        logsTable.setItems(logs);
        loadLogs();
    }

    @FXML
    private void onRefreshClicked() {
        loadLogs();
    }

    @FXML
    private void onBackClicked(ActionEvent event) {
        try {
            ViewNavigator.switchScene(event, "main-view.fxml", 800, 600, "File Manager - Main");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // private void loadLogs() {
    //     logs.setAll(eventLogRepository.findRecentLogs(100));
    // }
}
