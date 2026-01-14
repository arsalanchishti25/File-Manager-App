package com.example.mainapp.util;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Node;

import java.io.IOException;

import com.example.mainapp.App;

public class ViewNavigator {

    public static void switchScene(ActionEvent event,
                                   String fxmlName,
                                   double width,
                                   double height,
                                   String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(fxmlName));
        Scene scene = new Scene(loader.load(), width, height);

        // Always apply global stylesheet
        scene.getStylesheets().add(
                App.class.getResource("application.css").toExternalForm()
        );

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle(title);
        stage.show();
    }

    public static void switchScene(Stage stage, String fxml, double w, double h, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(fxml));
        Scene scene = new Scene(loader.load(), w, h);

        // Always apply global stylesheet
        scene.getStylesheets().add(
                App.class.getResource("application.css").toExternalForm()
        );

        stage.setScene(scene);
        stage.setTitle(title);
        stage.show();
    }

    public static Stage openModal(Stage owner,
                                  String fxmlName,
                                  double width,
                                  double height,
                                  String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource(fxmlName));
        Scene scene = new Scene(loader.load(), width, height);

        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.show(); // or showAndWait()

        return stage; // and you could also return loader.getController() if needed
    }

    @FXML
    public static void closeWindow(ActionEvent event) {
        Node node = (Node) event.getSource();   // this is your Node
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close(); // or use your utility
    }

}
