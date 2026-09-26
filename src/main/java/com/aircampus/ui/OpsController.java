package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.service.FlightService;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class OpsController extends BaseController {
  @FXML private TableView<Flight> flightsTable;
  @FXML private CheckBox historyCheck;

  @Override
  protected void setup() {
    Tables.flights(flightsTable);
    Ui.column(flightsTable, "Preflight", Flight::preflight);
    Ui.column(flightsTable, "Weight checked", Flight::weightChecked);
  }

  @Override
  protected void refreshData() {
    Ui.replace(
        flightsTable,
        c.flights.all(s).stream()
            .filter(
                f ->
                    historyCheck.isSelected()
                        || f.estimatedArrival() >= c.flights.now()
                        || f.status() == FlightStatus.IN_AIR)
            .toList());
  }

  private Flight flight() {
    return Ui.selected(flightsTable, "a flight");
  }

  @FXML
  public void onCreate() {
    safe(() -> Forms.schedule(c, s, stage, null));
  }

  @FXML
  public void onEdit() {
    safe(() -> Forms.schedule(c, s, stage, flight()));
  }

  @FXML
  public void onGate() {
    safe(
        () -> {
          Flight f = flight();
          Ui.Form form = new Ui.Form(stage, "Assign gate");
          ComboBox<String> gate =
              form.add("Gate", Ui.choice(FlightService.GATES.toArray(String[]::new)));
          form.submit(() -> c.flights.assignGate(s, f.id(), gate.getValue()));
        });
  }

  @FXML
  public void onCrew() {
    safe(() -> Forms.crew(c, s, stage, flight()));
  }

  @FXML
  public void onStatus() {
    safe(() -> Forms.status(c, s, stage, flight()));
  }

  @FXML
  public void onPreflight() {
    safe(() -> Forms.preflight(c, s, stage, flight()));
  }

  @FXML
  public void onWeight() {
    safe(() -> Forms.weight(c, s, stage, flight()));
  }

  @FXML
  public void onManifest() {
    safe(() -> Forms.manifest(c, s, stage, flight()));
  }
}
