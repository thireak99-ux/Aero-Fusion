package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class ManagerController extends BaseController {
  private String lastWeather;
  @FXML private TextArea reportArea;
  @FXML private TableView<Incident> incidentsTable;
  @FXML private TableView<AuditEntry> auditTable;
  @FXML private TableView<Claim> claimsTable;
  @FXML private ComboBox<String> weatherBox;

  @Override
  protected void setup() {
    weatherBox.getItems().setAll("CLEAR", "LOW_VISIBILITY", "STORM");
    Tables.incidents(incidentsTable);
    Ui.table(auditTable);
    Ui.column(auditTable, "Time", a -> Formats.time(a.createdAt()));
    Ui.column(auditTable, "User", AuditEntry::actor);
    Ui.column(auditTable, "Action", AuditEntry::action);
    Ui.column(auditTable, "Details", AuditEntry::details);
    Ui.table(claimsTable);
    Ui.column(claimsTable, "ID", Claim::id);
    Ui.column(claimsTable, "Booking", Claim::reference);
    Ui.column(claimsTable, "Description", Claim::description);
    Ui.column(claimsTable, "Status", Claim::status);
  }

  @Override
  protected void refreshData() {
    reportArea.setText(
        c.operations.report(s)
            + "\nRUNWAY ALLOCATIONS\n"
            + c.atc.queue(s).stream()
                .map(
                    r ->
                        c.flights.get(s, r.flightId()).number()
                            + " | "
                            + r.runway()
                            + " | "
                            + r.operation()
                            + " | "
                            + r.state())
                .collect(java.util.stream.Collectors.joining("\n")));
    Ui.replace(incidentsTable, c.operations.incidents(s));
    Ui.replace(auditTable, c.operations.auditLog(s));
    Ui.replace(claimsTable, c.operations.claims(s));
    String currentWeather = c.atc.weather(s);
    if (lastWeather == null || java.util.Objects.equals(weatherBox.getValue(), lastWeather))
      weatherBox.setValue(currentWeather);
    lastWeather = currentWeather;
  }

  @FXML
  public void onWeather() {
    safe(() -> c.atc.weather(s, weatherBox.getValue()));
  }

  @FXML
  public void onStaff() {
    safe(
        () ->
            Ui.textWindow(
                stage,
                "Staff directory",
                c.auth.users(s).stream()
                    .filter(u -> u.role() != Role.PASSENGER)
                    .map(User::toString)
                    .collect(java.util.stream.Collectors.joining("\n"))));
  }

  @FXML
  public void onResolve() {
    safe(
        () -> {
          Incident i = Ui.selected(incidentsTable, "an incident");
          Ui.Form f = new Ui.Form(stage, "Resolve incident #" + i.id());
          TextField resolution = f.add("Resolution", new TextField());
          f.submit(() -> c.operations.resolveIncident(s, i.id(), resolution.getText()));
        });
  }

  @FXML
  public void onClaim() {
    safe(
        () -> {
          Claim claim = Ui.selected(claimsTable, "a baggage claim");
          Ui.Form f = new Ui.Form(stage, "Update baggage claim");
          ComboBox<String> status =
              f.add("Next status", Ui.choice("SEARCHING", "FOUND", "RETURNED"));
          f.submit(() -> c.operations.updateClaim(s, claim.id(), status.getValue()));
        });
  }

  @FXML
  public void onWatchlist() {
    safe(
        () -> {
          Ui.Form f = new Ui.Form(stage, "Manage training watchlist");
          TextField name = f.add("Full name", new TextField());
          TextField reason = f.add("Reason (for adding)", new TextField());
          ComboBox<String> action = f.add("Action", Ui.choice("Add", "Remove"));
          f.note(String.join("; ", c.security.watchlist(s)));
          f.submit(
              () -> {
                if (action.getValue().equals("Add"))
                  c.security.addWatchlist(s, name.getText(), reason.getText());
                else c.security.removeWatchlist(s, name.getText());
              });
        });
  }

  @FXML
  public void onExport() {
    safe(() -> Forms.exportText(stage, "airport-report.txt", reportArea.getText()));
  }
}
