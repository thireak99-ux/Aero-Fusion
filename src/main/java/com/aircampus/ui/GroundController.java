package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.util.Checks;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class GroundController extends BaseController {
  @FXML private ComboBox<Flight> flightBox;
  @FXML private TableView<GroundTask> tasksTable;
  @FXML private ProgressBar progressBar;
  @FXML private Label progressLabel;

  @Override
  protected void setup() {
    Ui.table(tasksTable);
    Ui.column(tasksTable, "Task", GroundTask::name);
    Ui.column(tasksTable, "Team", GroundTask::assignedTeam);
    Ui.column(tasksTable, "Status", GroundTask::status);
    flightBox
        .valueProperty()
        .addListener(
            (o, a, b) -> {
              if (!refreshing) refresh();
            });
  }

  @Override
  protected void refreshData() {
    Ui.replace(
        flightBox,
        c.flights.all(s).stream()
            .filter(f -> f.status().isOpen() && f.estimatedDeparture() > c.flights.now() - 3600)
            .toList());
    if (flightBox.getValue() == null) {
      tasksTable.getItems().clear();
      return;
    }
    var tasks = c.ground.tasks(s, flightBox.getValue().id());
    Ui.replace(tasksTable, tasks);
    long done = tasks.stream().filter(t -> t.status().equals("DONE")).count();
    progressBar.setProgress(tasks.isEmpty() ? 0 : done / (double) tasks.size());
    progressLabel.setText(
        done
            + " / "
            + tasks.size()
            + " mandatory tasks complete | Pushback: "
            + (c.flights.get(s, flightBox.getValue().id()).pushback()
                ? "Requested"
                : "Not requested"));
  }

  @FXML
  public void onAdvance() {
    safe(() -> c.ground.advance(s, Ui.selected(tasksTable, "a task").id()));
  }

  @FXML
  public void onPushback() {
    safe(() -> c.ground.pushback(s, Checks.required(flightBox.getValue(), "a flight").id()));
  }
}
