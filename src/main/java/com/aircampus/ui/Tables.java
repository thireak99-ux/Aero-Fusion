package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.util.Formats;
import javafx.scene.control.*;

public final class Tables {
  private Tables() {}

  public static void flights(TableView<Flight> t) {
    Ui.table(t);
    Ui.column(t, "Flight", Flight::number);
    Ui.column(t, "From", f -> f.origin().name());
    Ui.column(t, "To", f -> f.destination().name());
    Ui.column(t, "Scheduled", f -> Formats.time(f.departure()));
    Ui.column(t, "Estimated", f -> Formats.time(f.estimatedDeparture()));
    Ui.column(t, "Gate", f -> f.gate().isBlank() ? "TBA" : f.gate());
    TableColumn<Flight, String> status = Ui.column(t, "Status", Flight::status);
    status.setCellFactory(
        col ->
            new TableCell<>() {
              @Override
              protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                String color =
                    value == null
                        ? "#475569"
                        : switch (value) {
                          case "CANCELLED" -> "#b91c1c";
                          case "DELAYED" -> "#b45309";
                          case "BOARDING" -> "#2563eb";
                          default -> "#047857";
                        };
                setStyle("-fx-font-weight:bold;-fx-text-fill:" + color + ";");
              }
            });
  }

  public static void bookings(TableView<Booking> t) {
    Ui.table(t);
    Ui.column(t, "Reference", Booking::reference);
    Ui.column(t, "Passenger", Booking::passengerName);
    Ui.column(t, "Seat", Booking::seat);
    Ui.column(t, "Fare", Booking::fareClass);
    Ui.column(t, "Status", Booking::status);
    Ui.column(t, "Bags", b -> b.baggagePieces() + " / " + b.baggageKg() + " kg");
    Ui.column(t, "Assistance", Booking::assistance);
    Ui.column(t, "Meal", Booking::meal);
  }

  public static void incidents(TableView<Incident> t) {
    Ui.table(t);
    Ui.column(t, "ID", Incident::id);
    Ui.column(t, "Flight ID", Incident::flightId);
    Ui.column(t, "Time", i -> Formats.time(i.happenedAt()));
    Ui.column(t, "Location", Incident::location);
    Ui.column(t, "Severity", Incident::severity);
    Ui.column(t, "Status", Incident::status);
    Ui.column(t, "Description", Incident::description);
  }
}
