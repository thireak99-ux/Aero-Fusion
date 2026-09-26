package com.aircampus.ui;

import com.aircampus.*;
import com.aircampus.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.util.Duration;

public abstract class LiveController implements ScreenController {
  protected AppContext c;
  protected Session s;
  protected Stage stage;
  protected boolean disposed;
  private Timeline timer;
  private AutoCloseable subscription;
  @FXML private Button navDashboardButton, navBoardButton, navTrackingButton, navDemoButton;

  @Override
  public void attach(AppContext c, Session s, Stage stage) {
    this.c = c;
    this.s = s;
    this.stage = stage;
    setup();
    if (navBoardButton != null) navBoardButton.setDisable(this instanceof BoardController);
    if (navTrackingButton != null) navTrackingButton.setDisable(this instanceof MapController);
    if (navDemoButton != null) {
      boolean manager = s != null && s.user().role() == Role.MANAGER;
      navDemoButton.setVisible(manager);
      navDemoButton.setManaged(manager);
      navDemoButton.setDisable(this instanceof DemoController);
    }
    update();
    subscription =
        c.events.subscribe(
            e ->
                Platform.runLater(
                    () -> {
                      if (!disposed) update();
                    }));
    timer =
        new Timeline(
            new KeyFrame(
                Duration.seconds(5),
                e -> {
                  if (!disposed) update();
                }));
    timer.setCycleCount(Timeline.INDEFINITE);
    timer.play();
  }

  protected abstract void setup();

  protected abstract void update();

  protected void safe(Runnable work) {
    try {
      work.run();
    } catch (RuntimeException e) {
      Ui.error(stage, e);
    }
  }

  /** Return to the still-open role dashboard, keeping its selected tab and session. */
  @FXML
  public void onDashboard() {
    safe(
        () -> {
          if (stage.getOwner() instanceof Stage owner && owner.isShowing()) {
            stage.close();
            owner.toFront();
            owner.requestFocus();
          } else {
            ViewFactory.dashboard(c, s, stage);
          }
        });
  }

  private void navigate(String view, String title) {
    safe(
        () -> {
          c.auth.require(s);
          ViewFactory.show(c, s, stage, view, title);
          stage.toFront();
        });
  }

  @FXML
  public void onFlightBoard() {
    navigate("board", "Departure / arrival board");
  }

  @FXML
  public void onTracking() {
    navigate("map", "Flight tracking");
  }

  @FXML
  public void onDemoNavigation() {
    safe(
        () -> {
          c.auth.require(s, Role.MANAGER);
          ViewFactory.show(c, s, stage, "demo", "Demo and time controls");
          stage.toFront();
        });
  }

  @Override
  public void dispose() {
    disposed = true;
    if (timer != null) timer.stop();
    if (subscription != null)
      try {
        subscription.close();
      } catch (Exception e) {
        System.err.println(e.getMessage());
      }
  }
}
