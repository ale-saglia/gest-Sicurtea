package com.sicurtea.gest.dclient;

import java.io.IOException;
import javafx.fxml.FXML;

public class MainController {

    @FXML
    private void switchToSecondary() throws IOException {
        App.setRoot("secondary");
    }
}