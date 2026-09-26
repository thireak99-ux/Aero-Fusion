package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.service.CheckInService;
import com.aircampus.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class CheckinController extends BaseController {
  @FXML private ComboBox<String> counterBox;
  @FXML private ComboBox<Flight> flightBox;
  @FXML private TextField lookupField;
  @FXML private Label counterLabel;
  @FXML private TableView<Booking> manifestTable;
  private String filter = "";

  @Override
  protected void setup() {
    counterBox.getItems().setAll(CheckInService.COUNTERS);
    counterBox.setValue("C01");
    Tables.bookings(manifestTable);
    Ui.column(manifestTable, "ID verified", Booking::identityVerified);
  }

  @Override
  protected void refreshData() {
    Ui.replace(
        flightBox,
        c.flights.all(s).stream()
            .filter(
                f ->
                    f.status().isOpen()
                        && f.estimatedDeparture() >= c.flights.now() + 3600
                        && f.estimatedDeparture() <= c.flights.now() + 86400)
            .toList());
    counterLabel.setText(
        "Counter: "
            + c.checkin.currentCounter(s)
            + " | Assigned flight ID: "
            + c.checkin.assignedFlight(s));
    Ui.replace(
        manifestTable,
        c.checkin.manifest(s).stream()
            .filter(
                b ->
                    filter.isEmpty()
                        || b.reference().equalsIgnoreCase(filter)
                        || b.passport().equalsIgnoreCase(filter))
            .toList());
  }

  @FXML
  public void onOpen() {
    safe(
        () -> {
          c.checkin.open(
              s, counterBox.getValue(), Checks.required(flightBox.getValue(), "a flight").id());
          filter = "";
        });
  }

  @FXML
  public void onClose() {
    safe(() -> c.checkin.close(s));
  }

  @FXML
  public void onSearch() {
    safe(
        () -> {
          c.checkin.search(s, lookupField.getText());
          filter = lookupField.getText().trim();
        });
  }

  @FXML
  public void onManifest() {
    safe(() -> filter = "");
  }

  @FXML
  public void onVerify() {
    safe(
        () -> {
          Booking b = Ui.selected(manifestTable, "a passenger");
          Ui.Form f = new Ui.Form(stage, "Verify photo ID");
          TextField name = f.add("Name printed on ID", new TextField());
          TextField id = f.add("Passport / ID number", new TextField());
          f.submit(() -> c.checkin.verifyIdentity(s, b.reference(), id.getText(), name.getText()));
        });
  }

  @FXML
  public void onProcess() {
    safe(() -> Forms.counter(c, s, stage, Ui.selected(manifestTable, "a passenger")));
  }

  @FXML
  public void onRequests() {
    safe(() -> Forms.requests(c, s, stage, Ui.selected(manifestTable, "a passenger")));
  }

  @FXML
  public void onPass() {
    safe(
        () -> {
          Booking b = Ui.selected(manifestTable, "a passenger");
          c.bookings.boardingPass(s, b.reference());
          ((BoardingPassController) showWorkspace("boarding-pass", "Boarding pass"))
              .setReference(b.reference());
        });
  }
}
