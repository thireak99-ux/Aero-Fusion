package com.aircampus;

import com.aircampus.ui.ViewFactory;
import java.nio.file.Path;
import java.time.Clock;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public final class AirportApplication extends Application {
  private AppContext context;

  @Override
  public void start(Stage stage) {
    try {
      context =
          new AppContext(
              Path.of(System.getProperty("airport.data", "data/airport-csv")),
              Clock.systemUTC(),
              true);
      ViewFactory.login(context, stage);
    } catch (RuntimeException e) {
      e.printStackTrace();
      Alert a =
          new Alert(Alert.AlertType.ERROR, "The airport app could not start. " + e.getMessage());
      a.setHeaderText("Startup error");
      a.showAndWait();
      javafx.application.Platform.exit();
    }
  }

  @Override
  public void stop() {
    if (context != null) context.close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
