package com.aircampus.ui;

import com.aircampus.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class SecurityController extends BaseController {
  @FXML private TableView<Booking> securityTable;

  @Override
  protected void setup() {
    Tables.bookings(securityTable);
    Ui.column(securityTable, "Passenger check", Booking::passengerScreen);
    Ui.column(securityTable, "Baggage check", Booking::baggageScreen);
    Ui.column(securityTable, "Gate cleared", Booking::gateCleared);
  }

  @Override
  protected void refreshData() {
    Ui.replace(securityTable, c.security.queue(s));
  }

  private void screen(boolean baggage) {
    Booking b = Ui.selected(securityTable, "a passenger");
    Ui.Form f = new Ui.Form(stage, baggage ? "Baggage X-ray simulation" : "Passenger screening");
    ComboBox<String> result = f.add("Result", Ui.choice("PASS", "FAIL"));
    TextField items = f.add("Prohibited items / reason", Ui.text("None"));
    f.note("A failed screen needs a reason. Escalate serious findings using Report incident.");
    f.submit(
        () -> c.security.screen(s, b.reference(), baggage, result.getValue(), items.getText()));
  }

  @FXML
  public void onPassenger() {
    safe(() -> screen(false));
  }

  @FXML
  public void onBaggage() {
    safe(() -> screen(true));
  }

  @FXML
  public void onClear() {
    safe(
        () -> {
          Booking b = Ui.selected(securityTable, "a passenger");
          Ui.Form f = new Ui.Form(stage, "Validate boarding pass and photo ID");
          TextField name = f.add("Name printed on ID", new TextField());
          TextField id = f.add("Passport / ID number", new TextField());
          f.note(
              "The current booking is rechecked for cancellation, screening results and the"
                  + " watchlist.");
          f.submit(() -> c.security.gateClearance(s, b.reference(), name.getText(), id.getText()));
        });
  }

  @FXML
  public void onWatchlist() {
    safe(
        () ->
            Ui.textWindow(stage, "Training watchlist", String.join("\n", c.security.watchlist(s))));
  }
}
