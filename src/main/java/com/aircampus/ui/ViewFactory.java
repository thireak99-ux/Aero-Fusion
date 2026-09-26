package com.aircampus.ui;

import com.aircampus.*;
import com.aircampus.model.*;
import java.io.IOException;
import java.util.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

public final class ViewFactory {
  private ViewFactory() {}

  public static ScreenController show(
      AppContext context, Session session, Stage stage, String file, String title) {
    try {
      FXMLLoader loader =
          new FXMLLoader(ViewFactory.class.getResource("/com/aircampus/view/" + file + ".fxml"));
      Parent root = loader.load();
      if (stage.getUserData() instanceof ScreenController old) old.dispose();
      Scene scene = new Scene(root, 1180, 780);
      scene
          .getStylesheets()
          .add(
              Objects.requireNonNull(ViewFactory.class.getResource("/com/aircampus/style/app.css"))
                  .toExternalForm());
      if (session != null) root.setStyle("-role-accent: " + session.user().role().color() + ";");
      stage.setTitle("AeroFusion | " + title);
      stage.setMinWidth(1024);
      stage.setMinHeight(680);
      stage.setScene(scene);
      stage.setResizable(true);
      ScreenController controller = loader.getController();
      stage.setUserData(controller);
      controller.attach(context, session, stage);
      stage.setOnHidden(
          e -> {
            controller.dispose();
            if (session != null && controller instanceof BaseController)
              context.auth.logout(session);
          });
      stage.show();
      return controller;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load screen " + file + ". Check its FXML file.", e);
    }
  }

  public static void login(AppContext c, Stage stage) {
    show(c, null, stage, "login", "Sign in");
  }

  public static void dashboard(AppContext c, Session s, Stage stage) {
    c.auth.require(s);
    show(c, s, stage, s.user().role().view(), s.user().role().toString());
  }
}
