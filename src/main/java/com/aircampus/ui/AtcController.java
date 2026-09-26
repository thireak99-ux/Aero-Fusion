package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.service.AtcService;
import com.aircampus.util.*;
import java.time.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class AtcController extends BaseController {
  private String lastWeather;
  @FXML private ComboBox<Flight> flightBox;
  @FXML private ComboBox<String> operationBox, weatherBox;
  @FXML private TableView<RunwaySlot> queueTable;

  @Override
  protected void setup() {
    operationBox.getItems().setAll("TAKEOFF", "LANDING");
    operationBox.setValue("TAKEOFF");
    weatherBox.getItems().setAll("CLEAR", "LOW_VISIBILITY", "STORM");
    Ui.table(queueTable);
    Ui.column(
        queueTable,
        "Priority",
        r -> c.flights.get(s, r.flightId()).emergency() ? "EMERGENCY" : "Normal");
    Ui.column(queueTable, "Flight", r -> c.flights.get(s, r.flightId()).number());
    Ui.column(queueTable, "Operation", RunwaySlot::operation);
    Ui.column(queueTable, "Runway", RunwaySlot::runway);
    Ui.column(
        queueTable,
        "Slot start",
        r -> r.startTime() == 0 ? "Unassigned" : Formats.time(r.startTime()));
    Ui.column(
        queueTable, "Slot end", r -> r.endTime() == 0 ? "Unassigned" : Formats.time(r.endTime()));
    Ui.column(
        queueTable,
        "State",
        r -> r.endTime() > 0 && r.endTime() <= c.flights.now() ? "EXPIRED" : r.state());
  }

  @Override
  protected void refreshData() {
    Ui.replace(
        flightBox,
        c.flights.all(s).stream()
            .filter(
                f ->
                    (f.status().isOpen() && f.estimatedDeparture() > c.flights.now() - 3600)
                        || f.status() == FlightStatus.IN_AIR)
            .toList());
    Ui.replace(queueTable, c.atc.queue(s));
    String currentWeather = c.atc.weather(s);
    if (lastWeather == null || java.util.Objects.equals(weatherBox.getValue(), lastWeather))
      weatherBox.setValue(currentWeather);
    lastWeather = currentWeather;
  }

  private long selectedFlight() {
    return Ui.selected(queueTable, "a queue entry").flightId();
  }

  @FXML
  public void onQueue() {
    safe(
        () ->
            c.atc.enqueue(
                s,
                Checks.required(flightBox.getValue(), "a flight").id(),
                operationBox.getValue()));
  }

  @FXML
  public void onWeather() {
    safe(() -> c.atc.weather(s, weatherBox.getValue()));
  }

  @FXML
  public void onAssign() {
    safe(
        () -> {
          long id = selectedFlight();
          Ui.Form f = new Ui.Form(stage, "Reserve a 10-minute runway slot");
          ComboBox<String> runway =
              f.add("Runway direction", Ui.choice(AtcService.RUNWAYS.toArray(String[]::new)));
          DatePicker date = f.add("Date (Cambodia)", new DatePicker(c.flights.today()));
          TextField time =
              f.add(
                  "Start (HH:mm)",
                  Ui.text(
                      c.clock
                          .instant()
                          .atZone(Formats.ZONE)
                          .toLocalTime()
                          .withSecond(0)
                          .withNano(0)
                          .toString()));
          f.note(
              "RWY 01 and RWY 19 are opposite directions of the same physical runway."
                  + " Use the current time for the classroom demo.");
          f.submit(
              () ->
                  c.atc.assignRunway(
                      s,
                      id,
                      runway.getValue(),
                      Formats.timestamp(date.getValue(), time.getText())));
        });
  }

  @FXML
  public void onGrant() {
    safe(() -> c.atc.grant(s, selectedFlight()));
  }

  @FXML
  public void onHold() {
    safe(
        () -> {
          long id = selectedFlight();
          Ui.Form f = new Ui.Form(stage, "Assign hold");
          TextField reason = f.add("Reason", new TextField());
          f.submit(() -> c.atc.hold(s, id, reason.getText()));
        });
  }

  @FXML
  public void onEmergency() {
    safe(
        () -> {
          long id = selectedFlight();
          Ui.Form f = new Ui.Form(stage, "Declare emergency priority");
          TextField reason = f.add("Emergency reason", new TextField());
          f.note("Priority does not override occupied runway, readiness or weather" + " checks.");
          f.submit(() -> c.atc.emergency(s, id, reason.getText()));
        });
  }

  @FXML
  public void onRelease() {
    safe(() -> c.atc.release(s, selectedFlight()));
  }
}
