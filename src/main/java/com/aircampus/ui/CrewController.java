package com.aircampus.ui;

import com.aircampus.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class CrewController extends BaseController {
  @FXML private TableView<Flight> flightsTable;

  @Override
  protected void setup() {
    Tables.flights(flightsTable);
    Ui.column(flightsTable, "Preflight signed", Flight::preflight);
    Ui.column(flightsTable, "Weight checked", Flight::weightChecked);
  }

  @Override
  protected void refreshData() {
    Ui.replace(
        flightsTable,
        c.flights.all(s).stream()
            .filter(
                f ->
                    (f.status().isOpen() && f.estimatedDeparture() > c.flights.now() - 3600)
                        || f.status() == FlightStatus.DEPARTED
                        || f.status() == FlightStatus.IN_AIR)
            .toList());
  }

  @FXML
  public void onPreflight() {
    safe(() -> Forms.preflight(c, s, stage, Ui.selected(flightsTable, "a flight")));
  }

  @FXML
  public void onWeight() {
    safe(() -> Forms.weight(c, s, stage, Ui.selected(flightsTable, "a flight")));
  }

  @FXML
  public void onManifest() {
    safe(() -> Forms.manifest(c, s, stage, Ui.selected(flightsTable, "a flight")));
  }

  @FXML
  public void onStatus() {
    safe(() -> Forms.status(c, s, stage, Ui.selected(flightsTable, "a flight")));
  }
}
