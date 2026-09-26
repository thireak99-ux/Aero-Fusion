package com.aircampus.ui;

import com.aircampus.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class MaintenanceController extends BaseController {
  @FXML private TableView<Aircraft> aircraftTable;
  @FXML private ListView<String> historyList;

  @Override
  protected void setup() {
    Ui.table(aircraftTable);
    Ui.column(aircraftTable, "Aircraft", Aircraft::registration);
    Ui.column(aircraftTable, "Type", Aircraft::type);
    Ui.column(aircraftTable, "Seats", a -> a.rows() * 6);
    Ui.column(aircraftTable, "Empty kg", Aircraft::emptyKg);
    Ui.column(aircraftTable, "MTOW kg", Aircraft::maxTakeoffKg);
    Ui.column(aircraftTable, "Condition", a -> a.airworthy() ? "AIRWORTHY" : "GROUNDED");
  }

  @Override
  protected void refreshData() {
    Ui.replace(aircraftTable, c.flights.aircrafts(s));
    historyList.getItems().setAll(c.operations.inspectionLog(s));
  }

  @FXML
  public void onInspect() {
    safe(
        () -> {
          Aircraft a = Ui.selected(aircraftTable, "an aircraft");
          Ui.Form f = new Ui.Form(stage, "Aircraft inspection: " + a.registration());
          ComboBox<String> result = f.add("Result", Ui.choice("GROUNDED", "AIRWORTHY"));
          TextField notes = f.add("Inspection / defects", new TextField());
          CheckBox confirmed =
              f.add(
                  "Inspection checklist",
                  new CheckBox("Inspection completed and defects reviewed"));
          f.note(
              "Every inspection requires a new pre-flight sign-off and weight check on open"
                  + " flights.");
          f.submit(
              () -> {
                com.aircampus.util.Checks.that(
                    confirmed.isSelected(), "Complete the inspection checklist.");
                c.operations.inspect(
                    s, a.id(), result.getValue().equals("AIRWORTHY"), notes.getText());
              });
        });
  }
}
