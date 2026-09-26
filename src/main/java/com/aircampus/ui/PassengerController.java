package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.util.*;
import java.time.LocalDate;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class PassengerController extends BaseController {
  @FXML private ComboBox<Airport> originBox, destinationBox;
  @FXML private DatePicker datePicker;
  @FXML private TableView<Flight> flightsTable;
  @FXML private TableView<Booking> bookingsTable;
  @FXML private TableView<Claim> claimsTable;
  private Airport searchOrigin, searchDestination;
  private LocalDate searchDate;

  @Override
  protected void setup() {
    originBox.getItems().setAll(Airport.values());
    destinationBox.getItems().setAll(Airport.values());
    originBox.setValue(Airport.KTI);
    destinationBox.setValue(Airport.BKK);
    datePicker.setValue(
        c.flights.all(s).stream()
            .filter(f -> f.estimatedDeparture() > c.flights.now() + 3600)
            .map(f -> Formats.date(f.departure()))
            .findFirst()
            .orElse(c.flights.today()));
    Tables.flights(flightsTable);
    Ui.column(flightsTable, "From price", f -> Formats.money(f.priceCents()));
    Tables.bookings(bookingsTable);
    Ui.column(bookingsTable, "Flight", b -> c.flights.get(s, b.flightId()).number());
    Ui.column(bookingsTable, "Refunds", b -> Formats.money(b.refundCents()));
    Ui.table(claimsTable);
    Ui.column(claimsTable, "Claim", Claim::id);
    Ui.column(claimsTable, "Booking", Claim::reference);
    Ui.column(claimsTable, "Bag description", Claim::description);
    Ui.column(claimsTable, "Status", Claim::status);
  }

  @Override
  protected void refreshData() {
    if (searchDate != null && searchDate.isBefore(c.flights.today())) {
      searchDate = null;
      datePicker.setValue(c.flights.today());
    }
    Ui.replace(
        flightsTable,
        searchDate == null
            ? c.flights.all(s).stream()
                .filter(f -> f.status().isOpen() && f.estimatedDeparture() > c.flights.now() + 3600)
                .toList()
            : c.flights.search(s, searchOrigin, searchDestination, searchDate));
    Ui.replace(bookingsTable, c.bookings.mine(s));
    Ui.replace(claimsTable, c.bookings.claims(s));
  }

  private Booking selectedBooking() {
    return Ui.selected(bookingsTable, "a booking");
  }

  @FXML
  public void onSearch() {
    safe(
        () -> {
          var found =
              c.flights.search(
                  s, originBox.getValue(), destinationBox.getValue(), datePicker.getValue());
          searchOrigin = originBox.getValue();
          searchDestination = destinationBox.getValue();
          searchDate = datePicker.getValue();
          Ui.replace(flightsTable, found);
          if (found.isEmpty())
            Ui.info(stage, "No matching flights", "Try another date or use Show all flights.");
        });
  }

  @FXML
  public void onAllFlights() {
    safe(() -> searchDate = null);
  }

  @FXML
  public void onDetails() {
    safe(() -> Forms.flightDetails(c, s, stage, Ui.selected(flightsTable, "a flight")));
  }

  @FXML
  public void onBook() {
    safe(() -> Forms.book(c, s, stage, Ui.selected(flightsTable, "a flight")));
  }

  @FXML
  public void onCheckIn() {
    safe(
        () -> {
          c.bookings.webCheckIn(s, selectedBooking().reference());
          Ui.info(stage, "Check-in complete", "You can now view and export your boarding pass.");
        });
  }

  @FXML
  public void onPass() {
    safe(
        () -> {
          Booking b = selectedBooking();
          c.bookings.boardingPass(s, b.reference());
          BoardingPassController p =
              (BoardingPassController) showWorkspace("boarding-pass", "Boarding pass");
          p.setReference(b.reference());
        });
  }

  @FXML
  public void onBaggage() {
    safe(() -> Forms.baggage(c, s, stage, selectedBooking()));
  }

  @FXML
  public void onRequests() {
    safe(() -> Forms.requests(c, s, stage, selectedBooking()));
  }

  @FXML
  public void onCancel() {
    safe(
        () -> {
          Booking b = selectedBooking();
          long refund = c.bookings.refundQuote(s, b.reference());
          if (Ui.confirm(
              stage,
              "Cancel " + b.reference() + "?",
              "Simulated refund: "
                  + Formats.money(refund)
                  + ". At least 24h before departure: full fare refund. Inside"
                  + " 24h: 50% fare refund. Baggage fees are refunded."))
            c.bookings.cancel(s, b.reference());
        });
  }

  @FXML
  public void onReschedule() {
    safe(() -> Forms.reschedule(c, s, stage, selectedBooking()));
  }

  @FXML
  public void onTrackBooking() {
    safe(
        () -> {
          Booking b = selectedBooking();
          MapController map = (MapController) showWorkspace("map", "Track my flight");
          map.track(b.flightId());
        });
  }

  @FXML
  public void onLostBag() {
    safe(
        () -> {
          Booking b = selectedBooking();
          Ui.Form f = new Ui.Form(stage, "Report lost baggage");
          TextField description = f.add("Bag description", new TextField());
          f.submit(() -> c.bookings.reportLostBag(s, b.reference(), description.getText()));
        });
  }
}
