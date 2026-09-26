package com.aircampus.ui;

import com.aircampus.*;
import com.aircampus.model.*;
import com.aircampus.util.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public abstract class BaseController implements ScreenController {
  @FXML protected Label userLabel, statusLabel, summaryLabel;
  protected AppContext c;
  protected Session s;
  protected Stage stage;
  private Timeline timer;
  private AutoCloseable subscription;
  private Stage workspaceStage;
  protected boolean refreshing;
  private boolean disposed;

  @Override
  public final void attach(AppContext context, Session session, Stage window) {
    c = context;
    s = session;
    stage = window;
    userLabel.setText(s.user().fullName() + " • " + s.user().role());
    setup();
    refresh();
    subscription =
        c.events.subscribe(
            event ->
                Platform.runLater(
                    () -> {
                      if (!disposed) {
                        statusLabel.setText(
                            event.message() + " • " + Formats.time(c.flights.now()));
                        refresh();
                      }
                    }));
    timer =
        new Timeline(
            new KeyFrame(
                Duration.seconds(5),
                e -> {
                  try {
                    c.auth.heartbeat(s);
                    refresh();
                  } catch (RuntimeException ex) {
                    statusLabel.setText(ex.getMessage());
                  }
                }));
    timer.setCycleCount(Timeline.INDEFINITE);
    timer.play();
  }

  protected abstract void setup();

  protected abstract void refreshData();

  protected final void refresh() {
    if (refreshing || disposed) return;
    refreshing = true;
    try {
      c.demo.ensureAvailable();
      refreshData();
      long unread = c.bookings.notices(s).stream().filter(n -> !n.read()).count();
      summaryLabel.setText(
          c.flights.all(s).size()
              + " flights  •  "
              + unread
              + " new notifications  •  "
              + c.clock.label()
              + " (UTC+7)");
    } finally {
      refreshing = false;
    }
  }

  protected void safe(Runnable action) {
    try {
      action.run();
      refresh();
      statusLabel.setText("Saved / refreshed • " + Formats.time(c.flights.now()));
    } catch (RuntimeException e) {
      try {
        c.operations.audit(
            s,
            "UI_VALIDATION",
            e instanceof com.aircampus.exception.AppException
                ? e.getMessage()
                : e.getClass().getSimpleName());
      } catch (RuntimeException ignored) {
      }
      Ui.error(stage, e);
    }
  }

  @FXML
  public void onRefresh() {
    safe(this::refresh);
  }

  @FXML
  public void onLogout() {
    safe(
        () -> {
          for (javafx.stage.Window w :
              javafx.stage.Window.getWindows().toArray(javafx.stage.Window[]::new))
            if (w instanceof Stage child && child.getOwner() == stage) child.close();
          dispose();
          c.auth.logout(s);
          ViewFactory.login(c, stage);
        });
  }

  @FXML
  public void onDemoControls() {
    safe(
        () -> {
          c.auth.require(s, Role.MANAGER);
          showWorkspace("demo", "Demo and time controls");
        });
  }

  @FXML
  public void onOtherLogin() {
    ViewFactory.login(c, new Stage());
  }

  @FXML
  public void onBoard() {
    safe(() -> showWorkspace("board", "Departure / arrival board"));
  }

  @FXML
  public void onMap() {
    safe(() -> showWorkspace("map", "Flight tracking"));
  }

  protected ScreenController showWorkspace(String view, String title) {
    if (workspaceStage == null || !workspaceStage.isShowing()) {
      workspaceStage = new Stage();
      workspaceStage.initOwner(stage);
    }
    ScreenController screen = ViewFactory.show(c, s, workspaceStage, view, title);
    workspaceStage.toFront();
    return screen;
  }

  @FXML
  public void onNotifications() {
    safe(
        () -> {
          String text =
              c.bookings.notices(s).stream()
                  .map(n -> Formats.time(n.createdAt()) + " | " + n.message())
                  .collect(java.util.stream.Collectors.joining("\n\n"));
          Ui.textWindow(
              stage, "Your notifications", text.isBlank() ? "No notifications yet." : text);
          c.bookings.readNotices(s);
        });
  }

  @FXML
  public void onIncident() {
    safe(() -> Forms.incident(c, s, stage));
  }

  @Override
  public void dispose() {
    if (disposed) return;
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
