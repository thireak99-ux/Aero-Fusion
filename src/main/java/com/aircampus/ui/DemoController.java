package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.service.DemoService;
import com.aircampus.util.*;
import java.time.*;
import java.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class DemoController extends LiveController {
  @FXML private Label clockLabel, resultLabel;
  @FXML private ComboBox<Flight> flightBox;
  @FXML private ComboBox<DemoService.TimePoint> pointBox;
  @FXML private DatePicker datePicker;
  @FXML private TextField timeField;
  @FXML private TextArea checklistArea;
  private boolean refreshing;

  @Override
  protected void setup() {
    c.auth.require(s, Role.MANAGER);
    pointBox.getItems().setAll(DemoService.TimePoint.values());
    pointBox.setValue(DemoService.TimePoint.WORKFLOW);
    datePicker.setValue(c.flights.today());
    timeField.setText(
        c.clock.instant().atZone(Formats.ZONE).toLocalTime().withSecond(0).withNano(0).toString());
    flightBox
        .valueProperty()
        .addListener(
            (o, a, b) -> {
              if (!refreshing) update();
            });
  }

  @Override
  protected void update() {
    if (refreshing) return;
    refreshing = true;
    try {
      c.auth.require(s, Role.MANAGER);
      clockLabel.setText(c.clock.label() + " • Cambodia time (UTC+7)");
      List<Flight> all = new ArrayList<>(c.flights.all(s));
      all.sort(
          Comparator.comparing((Flight f) -> f.estimatedDeparture() < c.flights.now())
              .thenComparingLong(Flight::estimatedDeparture));
      Ui.replace(flightBox, all);
      Flight selected = flightBox.getValue();
      if (selected == null) {
        checklistArea.setText("Add a fresh demo week to begin.");
        return;
      }
      Flight f = c.flights.get(s, selected.id());
      Aircraft a = c.flights.getAircraft(s, f.aircraftId());
      var passengers = c.flights.manifest(s, f.id());
      var tasks = c.ground.tasks(s, f.id());
      checklistArea.setText(
          "SELECTED FLIGHT: "
              + f.number()
              + " | "
              + f.status()
              + "\nDeparture: "
              + Formats.time(f.estimatedDeparture())
              + " | Arrival: "
              + Formats.time(f.estimatedArrival())
              + "\nAircraft: "
              + a.registration()
              + " | Gate: "
              + (f.gate().isBlank() ? "Not assigned" : f.gate())
              + "\n\n"
              + "1. Passenger: book, baggage, special requests, simulated payment.\n"
              + "   Active bookings: "
              + passengers.size()
              + "\n"
              + "2. Check-in / Passenger: verify ID or web check-in, then boarding"
              + " pass.\n"
              + "   Checked in: "
              + passengers.stream().filter(Booking::checkedIn).count()
              + "\n"
              + "3. Security: passenger PASS, baggage PASS, validate photo ID.\n"
              + "   Gate-cleared passengers: "
              + passengers.stream().filter(Booking::gateCleared).count()
              + "\n"
              + "4. Maintenance: inspect the selected aircraft and set AIRWORTHY.\n"
              + "   Aircraft condition: "
              + (a.airworthy() ? "AIRWORTHY" : "GROUNDED")
              + "\n"
              + "5. Crew / Ops: pre-flight sign-off, then Ops sets BOARDING.\n"
              + "   Pre-flight signed: "
              + f.preflight()
              + " | Assigned crew: "
              + c.flights.assignedCrew(s, f.id()).size()
              + "\n"
              + "6. Ground: unloading, cleaning, catering, fueling, loading.\n"
              + "   Tasks complete: "
              + tasks.stream().filter(t -> t.status().equals("DONE")).count()
              + " / "
              + tasks.size()
              + "\n"
              + "7. Crew: weight check; Ground: request pushback afterward.\n"
              + "   Weight checked: "
              + f.weightChecked()
              + " | Pushback requested: "
              + f.pushback()
              + "\n"
              + "8. ATC: CLEAR weather, current 10-minute runway slot, grant"
              + " clearance.\n"
              + "9. Ops: DEPARTED; Crew: IN AIR. ATC: LANDING queue/clearance; Crew:"
              + " LANDED.\n\n"
              + "Time presets test opening/cutoff and cancellation rules. They do not"
              + " advance flight status or bypass validation.");
    } catch (RuntimeException e) {
      resultLabel.setText(e.getMessage());
    } finally {
      refreshing = false;
    }
  }

  private void complete(String text) {
    resultLabel.setText(text);
    update();
  }

  @FXML
  public void onNewWeek() {
    safe(
        () -> {
          List<Flight> week = c.demo.newWeek(s);
          update();
          flightBox.setValue(week.get(0));
          complete(
              "Added "
                  + week.size()
                  + " flights. Begin with "
                  + week.get(0).number()
                  + "; prior bookings remain saved.");
        });
  }

  @FXML
  public void onPreset() {
    safe(
        () -> {
          c.demo.useFlightPoint(
              s, Checks.required(flightBox.getValue(), "a flight").id(), pointBox.getValue());
          complete(
              "Demo time is paused. Reopen any check-in counters and obtain a new ATC"
                  + " clearance.");
        });
  }

  @FXML
  public void onFreeze() {
    safe(
        () -> {
          c.demo.freezeNow(s);
          complete(
              "Current demo time paused. It will not expire while you demonstrate the"
                  + " workflow.");
        });
  }

  @FXML
  public void onRealTime() {
    safe(
        () -> {
          c.demo.useRealTime(s);
          complete(
              "Using the computer clock. Fresh flights are added automatically when" + " needed.");
        });
  }

  @FXML
  public void onSetTime() {
    safe(
        () -> {
          c.demo.setTime(
              s,
              Instant.ofEpochSecond(Formats.timestamp(datePicker.getValue(), timeField.getText())));
          complete(
              "Custom demo date/time applied and paused. Add a fresh week for this"
                  + " date if needed.");
        });
  }
}
