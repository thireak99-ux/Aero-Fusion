package com.aircampus.ui;

import com.aircampus.api.TrackingService;
import com.aircampus.model.*;
import com.aircampus.util.*;
import java.util.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public final class MapController extends LiveController {
  @FXML private Canvas mapCanvas;
  @FXML private StackPane mapPane;
  @FXML private Label sourceLabel, matchLabel;
  @FXML private CheckBox liveCheck;
  @FXML private ComboBox<Flight> flightBox;
  @FXML private TableView<Position> positionsTable;
  private boolean all = true, busy;
  private List<Position> shown = List.of();

  @Override
  protected void setup() {
    Ui.table(positionsTable);
    Ui.column(positionsTable, "Callsign", Position::callsign);
    Ui.column(positionsTable, "ICAO24", Position::icao24);
    Ui.column(positionsTable, "Latitude", p -> String.format(Locale.US, "%.4f", p.latitude()));
    Ui.column(positionsTable, "Longitude", p -> String.format(Locale.US, "%.4f", p.longitude()));
    Ui.column(positionsTable, "Altitude m", p -> Math.round(p.altitudeMeters()));
    Ui.column(positionsTable, "Speed m/s", p -> Math.round(p.speedMetersSecond()));
    Ui.column(positionsTable, "Source", p -> p.simulated() ? "DEMO" : "OpenSky");
    mapCanvas.widthProperty().bind(mapPane.widthProperty());
    mapCanvas.heightProperty().bind(mapPane.heightProperty());
    mapCanvas.widthProperty().addListener((o, a, b) -> draw());
    mapCanvas.heightProperty().addListener((o, a, b) -> draw());
  }

  public void track(long id) {
    all = false;
    Ui.replace(flightBox, c.flights.all(s));
    flightBox.getItems().stream()
        .filter(f -> f.id() == id)
        .findFirst()
        .ifPresent(flightBox::setValue);
    render(c.tracking.snapshot());
  }

  @Override
  protected void update() {
    if (busy) return;
    try {
      Ui.replace(flightBox, c.flights.all(s));
      liveCheck.setSelected(c.tracking.isLiveMode());
      busy = true;
      c.tracking
          .refresh()
          .whenComplete(
              (snapshot, error) ->
                  Platform.runLater(
                      () -> {
                        busy = false;
                        if (disposed) return;
                        if (error != null) {
                          sourceLabel.setText("Tracking unavailable");
                          return;
                        }
                        render(snapshot);
                      }));
    } catch (RuntimeException e) {
      sourceLabel.setText("Session ended");
      dispose();
    }
  }

  @FXML
  public void onMode() {
    safe(
        () -> {
          c.tracking.setLiveMode(liveCheck.isSelected());
          update();
        });
  }

  @FXML
  public void onTrack() {
    safe(
        () -> {
          Checks.required(flightBox.getValue(), "a flight");
          all = false;
          render(c.tracking.snapshot());
        });
  }

  @FXML
  public void onAll() {
    all = true;
    render(c.tracking.snapshot());
  }

  @FXML
  public void onRefresh() {
    safe(this::update);
  }

  private void render(TrackingService.Snapshot snapshot) {
    Flight f = flightBox.getValue();
    shown =
        snapshot.positions().stream()
            .filter(
                p ->
                    all
                        || f == null
                        || (!f.icao24().isBlank()
                            ? p.icao24().equalsIgnoreCase(f.icao24())
                            : !f.callsign().isBlank()
                                && p.callsign().equalsIgnoreCase(f.callsign())))
            .toList();
    Ui.replace(positionsTable, shown);
    sourceLabel.setText(
        snapshot.label()
            + (snapshot.fetchedAt() > 0 ? " • " + Formats.time(snapshot.fetchedAt()) : ""));
    matchLabel.setText(
        all
            ? shown.size() + " aircraft received"
            : shown.isEmpty()
                ? "No matching position received for "
                    + f.number()
                    + ". Demo flights have no real API track."
                : "Tracking "
                    + f.number()
                    + " • "
                    + (snapshot.live()
                        ? "Exact ICAO24 / callsign match"
                        : "Illustrative demo route"));
    draw();
  }

  private double x(double lon) {
    return 45 + (lon - 99) / 9 * (mapCanvas.getWidth() - 90);
  }

  private double y(double lat) {
    return 30 + (23 - lat) / 23 * (mapCanvas.getHeight() - 60);
  }

  private void draw() {
    if (mapCanvas == null) return;
    GraphicsContext g = mapCanvas.getGraphicsContext2D();
    double w = mapCanvas.getWidth(), h = mapCanvas.getHeight();
    g.setFill(Color.web("#0b1729"));
    g.fillRect(0, 0, w, h);
    g.setLineWidth(1);
    g.setStroke(Color.web("#253953"));
    g.setFill(Color.web("#7791b1"));
    for (int lon = 99; lon <= 108; lon++) {
      g.strokeLine(x(lon), 30, x(lon), h - 30);
      g.fillText(lon + "°E", x(lon) - 10, h - 10);
    }
    for (int lat = 0; lat <= 23; lat += 5) {
      g.strokeLine(45, y(lat), w - 45, y(lat));
      g.fillText(lat + "°N", 7, y(lat) + 4);
    }
    if (!all && flightBox.getValue() != null) {
      Flight f = flightBox.getValue();
      g.setStroke(Color.web("#5a7696"));
      g.setLineDashes(5);
      g.strokeLine(
          x(f.origin().longitude()),
          y(f.origin().latitude()),
          x(f.destination().longitude()),
          y(f.destination().latitude()));
      g.setLineDashes();
    }
    for (Airport a : Airport.values()) {
      g.setFill(Color.web("#8ba6c8"));
      g.fillOval(x(a.longitude()) - 3, y(a.latitude()) - 3, 6, 6);
      g.fillText(a.name(), x(a.longitude()) + 7, y(a.latitude()) - 5);
    }
    for (Position p : shown) {
      g.setFill(p.simulated() ? Color.web("#fbbf24") : Color.web("#38bdf8"));
      g.fillOval(x(p.longitude()) - 4, y(p.latitude()) - 4, 8, 8);
      g.fillText(
          p.callsign().isBlank() ? p.icao24() : p.callsign(),
          x(p.longitude()) + 7,
          y(p.latitude()) + 13);
    }
  }
}
