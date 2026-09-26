package com.aircampus.ui;

import com.aircampus.*;
import com.aircampus.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public final class RegisterController implements ScreenController {
  @FXML private TextField nameField, emailField, usernameField, phoneField;
  @FXML private PasswordField passwordField, confirmField;
  private AppContext c;
  private Stage stage;

  @Override
  public void attach(AppContext c, Session s, Stage stage) {
    this.c = c;
    this.stage = stage;
  }

  @FXML
  public void onRegister() {
    try {
      com.aircampus.util.Checks.that(
          passwordField.getText().equals(confirmField.getText()), "Passwords do not match.");
      c.auth.register(
          nameField.getText(),
          emailField.getText(),
          usernameField.getText(),
          phoneField.getText(),
          passwordField.getText());
      Ui.info(stage, "Account created", "You can now log in as Passenger.");
      ViewFactory.login(c, stage);
    } catch (RuntimeException e) {
      c.operations.audit(
          null,
          "REGISTRATION_FAILED",
          e instanceof com.aircampus.exception.AppException
              ? e.getMessage()
              : e.getClass().getSimpleName());
      Ui.error(stage, e);
    }
  }

  @FXML
  public void onBack() {
    ViewFactory.login(c, stage);
  }
}
