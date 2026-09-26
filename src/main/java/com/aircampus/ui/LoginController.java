package com.aircampus.ui;

import com.aircampus.*;
import com.aircampus.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public final class LoginController implements ScreenController {
  @FXML private ComboBox<Role> roleBox;
  @FXML private TextField loginField;
  @FXML private PasswordField passwordField;
  private AppContext c;
  private Stage stage;

  @Override
  public void attach(AppContext c, Session s, Stage stage) {
    this.c = c;
    this.stage = stage;
    roleBox.getItems().setAll(Role.values());
    roleBox.setValue(Role.PASSENGER);
  }

  @FXML
  public void onDemo() {
    Role r = roleBox.getValue();
    if (r != null) {
      loginField.setText(r.demoEmail());
      passwordField.setText("Airport123");
    }
  }

  @FXML
  public void onLogin() {
    try {
      Session session =
          c.auth.login(loginField.getText(), passwordField.getText(), roleBox.getValue());
      passwordField.clear();
      ViewFactory.dashboard(c, session, stage);
    } catch (RuntimeException e) {
      c.operations.audit(null, "LOGIN_FAILED", "Credentials or role rejected");
      Ui.error(stage, e);
    }
  }

  @FXML
  public void onRegister() {
    try {
      ViewFactory.show(c, null, stage, "register", "Create passenger account");
    } catch (RuntimeException e) {
      Ui.error(stage, e);
    }
  }
}
