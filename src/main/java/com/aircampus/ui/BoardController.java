package com.aircampus.ui;

import com.aircampus.model.*;
import com.aircampus.util.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class BoardController extends LiveController {
  @FXML private TableView<Flight> boardTable;
  @FXML private Label updatedLabel;
  @FXML private ComboBox<String> rangeBox;
  @FXML private DatePicker boardDate;

  @Override
  protected void setup() {
    Tables.flights(boardTable);
    Ui.column(boardTable, "Scheduled arrival", f -> Formats.time(f.arrival()));
    Ui.column(boardTable, "Estimated arrival", f -> Formats.time(f.estimatedArrival()));
    rangeBox.getItems().setAll("Upcoming and active", "All dates / history", "Selected date");
    rangeBox.setValue("Upcoming and active");
    boardDate.setValue(c.flights.today());
  }

  @Override
  protected void update() {
    try {
      String range = rangeBox.getValue();
      var date = boardDate.getValue();
      Ui.replace(
          boardTable,
          c.flights.all(s).stream()
              .filter(
                  f ->
                      "All dates / history".equals(range)
                          || ("Selected date".equals(range)
                              ? date != null
                                  && (Formats.date(f.departure()).equals(date)
                                      || Formats.date(f.arrival()).equals(date))
                              : f.estimatedArrival() >= c.flights.now()
                                  || f.status() == FlightStatus.IN_AIR))
              .toList());
      updatedLabel.setText(
          c.clock.label()
              + " • Cambodia time (UTC+7) • "
              + boardTable.getItems().size()
              + " flights shown");
    } catch (RuntimeException e) {
      updatedLabel.setText(e.getMessage());
    }
  }

  @FXML
  public void onRefresh() {
    safe(this::update);
  }
}
