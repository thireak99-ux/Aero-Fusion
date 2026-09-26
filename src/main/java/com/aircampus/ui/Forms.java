package com.aircampus.ui;

import com.aircampus.*;
import com.aircampus.model.*;
import com.aircampus.service.*;
import com.aircampus.util.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

public final class Forms {
  private Forms() {}

  public static void book(AppContext c, Session s, Stage owner, Flight flight) {
    Ui.Form f = new Ui.Form(owner, "Book " + flight.number());
    f.note(
        flight.toString()
            + " • Business: rows 1–2. Economy: rows 3+. Name matching is a classroom declaration;"
            + " staff verify photo ID later.");
    TextField name = f.add("Passenger full name", Ui.text(s.user().fullName()));
    TextField idName = f.add("Full name printed on ID", new TextField());
    TextField passport = f.add("Passport / ID", new TextField());
    DatePicker dob = f.add("Date of birth", new DatePicker());
    dob.setPromptText("yyyy-MM-dd");
    ComboBox<FareClass> fare = f.add("Fare class", Ui.choice(FareClass.values()));
    ComboBox<String> seat = f.add("Seat", new ComboBox<>());
    Label price = f.add("Flight fare", new Label());
    Runnable update =
        () -> {
          Ui.replace(seat, c.bookings.seats(s, flight.id(), fare.getValue(), ""));
          price.setText(
              Formats.money(fare.getValue().price(c.flights.get(s, flight.id()).priceCents())));
        };
    fare.valueProperty().addListener((o, a, b) -> update.run());
    update.run();
    Button map = new Button("Choose from seat map");
    map.setOnAction(e -> chooseSeat(c, s, owner, flight, fare.getValue(), "", seat));
    f.add("Seat map", map);
    ComboBox<String> assistance =
        f.add("Assistance", Ui.choice("None", "Wheelchair", "Unaccompanied minor"));
    ComboBox<String> meal = f.add("Meal", Ui.choice("Standard", "Vegetarian", "Halal"));
    TextField guardianRef = f.add("Guardian booking reference", new TextField());
    TextField guardianName = f.add("Guardian full name", new TextField());
    TextField guardianPhone = f.add("Guardian phone", new TextField());
    f.note(
        "Under 18: link an adult booking on the same flight and provide guardian contact. Ages 5–17"
            + " may choose Unaccompanied minor instead, with guardian contact recorded on this"
            + " booking.");
    PaymentForm payment = new PaymentForm(f, c.clock);
    f.submit(
        () -> {
          Booking result =
              c.bookings.book(
                  s,
                  new BookingRequest(
                      flight.id(),
                      name.getText(),
                      idName.getText(),
                      passport.getText(),
                      dob.getValue(),
                      guardianRef.getText(),
                      guardianName.getText(),
                      guardianPhone.getText(),
                      seat.getValue(),
                      fare.getValue(),
                      assistance.getValue(),
                      meal.getValue()),
                  payment.details());
          Ui.info(
              owner,
              "Booking confirmed",
              "Reference: "
                  + result.reference()
                  + "\nFlight: "
                  + flight.number()
                  + "\nSeat: "
                  + result.seat()
                  + "\nSimulated fare paid: "
                  + Formats.money(result.paidCents()));
        });
  }

  public static void chooseSeat(
      AppContext c,
      Session s,
      Stage owner,
      Flight flight,
      FareClass fare,
      String except,
      ComboBox<String> selection) {
    List<String> available = c.bookings.seats(s, flight.id(), fare, except);
    Aircraft aircraft = c.flights.getAircraft(s, flight.aircraftId());
    Dialog<Void> d = new Dialog<>();
    d.initOwner(owner);
    d.setTitle("Seat map • " + flight.number());
    d.setHeaderText("Choose a seat • " + fare + " • grey seats are unavailable");
    d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(7);
    grid.setStyle("-fx-padding:20;");
    for (int row = 1; row <= aircraft.rows(); row++)
      for (int col = 0; col < 6; col++) {
        String seat = row + "" + (char) ('A' + col);
        Button button = new Button(seat);
        button.setPrefWidth(50);
        button.setDisable(!available.contains(seat));
        button.setStyle(
            available.contains(seat)
                ? "-fx-background-color:#dbeafe;-fx-text-fill:#1d4ed8;"
                : "-fx-background-color:#e2e8f0;");
        button.setOnAction(
            e -> {
              Ui.replace(selection, c.bookings.seats(s, flight.id(), fare, except));
              selection.setValue(seat);
              d.close();
            });
        grid.add(button, col + (col >= 3 ? 1 : 0), row);
      }
    Label aisle = new Label("AISLE");
    grid.add(aisle, 3, 0);
    ScrollPane scroll = new ScrollPane(grid);
    scroll.setPrefViewportHeight(460);
    d.getDialogPane().setContent(scroll);
    d.showAndWait();
  }

  public static void flightDetails(AppContext c, Session s, Stage owner, Flight flight) {
    Flight f = c.flights.get(s, flight.id());
    Aircraft a = c.flights.getAircraft(s, f.aircraftId());
    Ui.Form form = new Ui.Form(owner, "Flight details • " + f.number());
    form.note(
        f.origin()
            + " → "
            + f.destination()
            + "\nScheduled: "
            + Formats.time(f.departure())
            + "\nEstimated: "
            + Formats.time(f.estimatedDeparture())
            + "\nArrival: "
            + Formats.time(f.estimatedArrival())
            + "\nAircraft: "
            + a.type()
            + " / "
            + a.registration()
            + "\nGate: "
            + (f.gate().isBlank() ? "To be assigned" : f.gate())
            + "\nEconomy: "
            + Formats.money(f.priceCents())
            + ", 1 bag / 23 kg total\nBusiness: "
            + Formats.money(FareClass.BUSINESS.price(f.priceCents()))
            + ", 2 bags / 32 kg total\n"
            + "Excess: $10 per extra kg + $25 per extra piece. Hard limit: 4 pieces, 32 kg each.");
    ComboBox<FareClass> fare = form.add("View fare seats", Ui.choice(FareClass.values()));
    ComboBox<String> seat = new ComboBox<>();
    Button map = new Button("View seat map");
    map.setOnAction(e -> chooseSeat(c, s, owner, f, fare.getValue(), "", seat));
    form.add("Seating", map);
    form.submit(() -> {});
  }

  public static void baggage(AppContext c, Session s, Stage owner, Booking selected) {
    Booking b = c.bookings.get(s, selected.reference());
    Ui.Form f = new Ui.Form(owner, "Add baggage • " + b.reference());
    TextField pieces = f.add("Total pieces (0–4)", Ui.text("" + b.baggagePieces()));
    TextField kg = f.add("Total weight (kg)", Ui.text("" + b.baggageKg()));
    Label fee = f.add("Excess fee", new Label());
    Button quote = new Button("Calculate extra amount");
    quote.setOnAction(
        e -> {
          try {
            long total =
                BookingService.baggageFee(
                    b.fareClass(),
                    Checks.integer(pieces.getText(), "Pieces", 0, 4),
                    Checks.decimal(kg.getText(), "Weight", 0, 128));
            fee.setText(
                "Total "
                    + Formats.money(total)
                    + " • Extra now "
                    + Formats.money(Math.max(0, total - b.baggageFeeCents())));
          } catch (RuntimeException ex) {
            Ui.error(owner, ex);
          }
        });
    f.add("Quote", quote);
    f.note(
        "Economy: 1 piece / 23 kg total. Business: 2 pieces / 32 kg total. Excess: $10/kg +"
            + " $25/piece. Each piece must weigh at most 32 kg; counter staff verify individual"
            + " bags.");
    PaymentForm payment = new PaymentForm(f, c.clock);
    f.submit(
        () ->
            c.bookings.addBaggage(
                s,
                b.reference(),
                Checks.integer(pieces.getText(), "Pieces", 0, 4),
                Checks.decimal(kg.getText(), "Weight", 0, 128),
                payment.details()));
  }

  public static void requests(AppContext c, Session s, Stage owner, Booking b) {
    Ui.Form f = new Ui.Form(owner, "Special requests • " + b.reference());
    ComboBox<String> assistance =
        f.add("Assistance", Ui.choice("None", "Wheelchair", "Unaccompanied minor"));
    assistance.setValue(b.assistance());
    ComboBox<String> meal = f.add("Meal", Ui.choice("Standard", "Vegetarian", "Halal"));
    meal.setValue(b.meal());
    TextField name = f.add("Guardian name (if minor)", Ui.text(b.guardianName()));
    TextField phone = f.add("Guardian phone (if minor)", Ui.text(b.guardianPhone()));
    f.submit(
        () ->
            c.bookings.requests(
                s,
                b.reference(),
                assistance.getValue(),
                meal.getValue(),
                name.getText(),
                phone.getText()));
  }

  public static void reschedule(AppContext c, Session s, Stage owner, Booking b) {
    Flight old = c.flights.get(s, b.flightId());
    Ui.Form f = new Ui.Form(owner, "Reschedule • " + b.reference());
    ComboBox<Flight> flight = f.add("New flight", new ComboBox<>());
    Ui.replace(
        flight,
        c.flights.all(s).stream()
            .filter(
                x ->
                    x.id() != old.id()
                        && x.origin() == old.origin()
                        && x.destination() == old.destination()
                        && x.status().isOpen()
                        && x.estimatedDeparture() > c.flights.now() + 3600)
            .toList());
    ComboBox<String> seat = f.add("New seat", new ComboBox<>());
    Label quote = f.add("Fare adjustment", new Label("Choose an available flight"));
    Runnable update =
        () -> {
          if (flight.getValue() != null) {
            Ui.replace(seat, c.bookings.seats(s, flight.getValue().id(), b.fareClass(), ""));
            long amount = c.bookings.rescheduleQuote(s, b.reference(), flight.getValue().id());
            quote.setText((amount >= 0 ? "Pay " : "Refund ") + Formats.money(Math.abs(amount)));
          }
        };
    flight.valueProperty().addListener((o, a, x) -> update.run());
    update.run();
    f.note(
        "Same route and fare class. Fare difference applies. A $25 change fee applies inside 24"
            + " hours. Changes close 60 minutes before departure. Checked-in passengers cannot"
            + " reschedule.");
    PaymentForm payment = new PaymentForm(f, c.clock);
    f.submit(
        () ->
            c.bookings.reschedule(
                s,
                b.reference(),
                Checks.required(flight.getValue(), "a new flight").id(),
                seat.getValue(),
                payment.details()));
  }

  public static void counter(AppContext c, Session s, Stage owner, Booking b) {
    Ui.Form f = new Ui.Form(owner, "Counter check-in • " + b.reference());
    ComboBox<String> seat = f.add("Confirm / change seat", new ComboBox<>());
    Ui.replace(seat, c.bookings.seats(s, b.flightId(), b.fareClass(), b.reference()));
    seat.setValue(b.seat());
    TextField pieces = f.add("Baggage pieces", Ui.text("" + b.baggagePieces()));
    TextField weight = f.add("Total baggage kg", Ui.text("" + b.baggageKg()));
    Label fee = f.add("Excess fee", new Label());
    Button quote = new Button("Calculate fee / refund");
    quote.setOnAction(
        e -> {
          try {
            long total =
                BookingService.baggageFee(
                    b.fareClass(),
                    Checks.integer(pieces.getText(), "Pieces", 0, 4),
                    Checks.decimal(weight.getText(), "Weight", 0, 128));
            fee.setText(
                "Total "
                    + Formats.money(total)
                    + " | Change "
                    + Formats.money(total - b.baggageFeeCents()));
          } catch (RuntimeException ex) {
            Ui.error(owner, ex);
          }
        });
    f.add("Quote", quote);
    CheckBox paid = f.add("Excess fee", new CheckBox("Simulated extra fee collected"));
    CheckBox bags = f.add("Individual bags", new CheckBox("Every bag is at most 32 kg"));
    f.note(
        "A reduction in previously paid baggage fees creates a simulated refund. Reprocessing at"
            + " the counter updates an existing check-in and baggage tags.");
    f.submit(
        () -> {
          String tags =
              c.checkin.process(
                  s,
                  b.reference(),
                  seat.getValue(),
                  Checks.integer(pieces.getText(), "Pieces", 0, 4),
                  Checks.decimal(weight.getText(), "Weight", 0, 128),
                  paid.isSelected(),
                  bags.isSelected());
          Ui.textWindow(owner, "Simulated baggage tags", tags);
        });
  }

  public static void schedule(AppContext c, Session s, Stage owner, Flight existing) {
    Ui.Form f =
        new Ui.Form(owner, existing == null ? "Create a flight" : "Edit " + existing.number());
    long dep = existing == null ? c.flights.now() + 86400 : existing.departure(),
        arr = existing == null ? dep + 7200 : existing.arrival();
    TextField number = f.add("Flight number", Ui.text(existing == null ? "" : existing.number()));
    ComboBox<Airport> origin = f.add("Origin", Ui.choice(Airport.values()));
    ComboBox<Airport> destination = f.add("Destination", Ui.choice(Airport.values()));
    origin.setValue(existing == null ? Airport.KTI : existing.origin());
    destination.setValue(existing == null ? Airport.BKK : existing.destination());
    DatePicker departure = f.add("Departure date", new DatePicker(Formats.date(dep)));
    TextField departureTime =
        f.add(
            "Departure time (HH:mm)",
            Ui.text(
                Instant.ofEpochSecond(dep)
                    .atZone(Formats.ZONE)
                    .toLocalTime()
                    .withSecond(0)
                    .withNano(0)
                    .toString()));
    DatePicker arrival = f.add("Arrival date", new DatePicker(Formats.date(arr)));
    TextField arrivalTime =
        f.add(
            "Arrival time (HH:mm)",
            Ui.text(
                Instant.ofEpochSecond(arr)
                    .atZone(Formats.ZONE)
                    .toLocalTime()
                    .withSecond(0)
                    .withNano(0)
                    .toString()));
    TextField price =
        f.add(
            "Economy price (USD)",
            Ui.text(
                existing == null
                    ? "100"
                    : String.format(Locale.US, "%.2f", existing.priceCents() / 100.0)));
    ComboBox<Aircraft> aircraft = f.add("Aircraft", new ComboBox<>());
    Ui.replace(aircraft, c.flights.aircrafts(s));
    if (existing != null) aircraft.setValue(c.flights.getAircraft(s, existing.aircraftId()));
    TextField callsign =
        f.add("API callsign (optional)", Ui.text(existing == null ? "" : existing.callsign()));
    TextField icao = f.add("ICAO24 (optional)", Ui.text(existing == null ? "" : existing.icao24()));
    f.note(
        "All schedule times use Cambodia time (UTC+7). API matching uses ICAO24, or exact callsign."
            + " Demo flights do not represent real airline flights.");
    f.submit(
        () -> {
          FlightDraft d =
              new FlightDraft(
                  number.getText().trim().toUpperCase(Locale.ROOT),
                  origin.getValue(),
                  destination.getValue(),
                  Formats.timestamp(departure.getValue(), departureTime.getText()),
                  Formats.timestamp(arrival.getValue(), arrivalTime.getText()),
                  Math.round(Checks.decimal(price.getText(), "Price", 0.01, 10000) * 100),
                  Checks.required(aircraft.getValue(), "an aircraft").id(),
                  callsign.getText().trim().toUpperCase(Locale.ROOT),
                  icao.getText().trim().toLowerCase(Locale.ROOT));
          if (existing == null) c.flights.create(s, d);
          else c.flights.edit(s, existing.id(), d);
        });
  }

  public static void crew(AppContext c, Session s, Stage owner, Flight flight) {
    Ui.Form f = new Ui.Form(owner, "Crew assignments • " + flight.number());
    f.note(
        "Current: "
            + c.flights.assignedCrew(s, flight.id()).stream()
                .map(CrewMember::toString)
                .collect(java.util.stream.Collectors.joining(", ")));
    ComboBox<String> action = f.add("Action", Ui.choice("Assign", "Remove"));
    ComboBox<CrewMember> crew = f.add("Crew member", new ComboBox<>());
    Ui.replace(crew, c.flights.crew(s));
    f.note(
        "One pilot, co-pilot and cabin crew member are required. Certification must cover the"
            + " flight and match the aircraft. Rest rule: 8 hours between flights.");
    f.submit(
        () -> {
          CrewMember member = Checks.required(crew.getValue(), "a crew member");
          if (action.getValue().equals("Assign")) c.flights.assignCrew(s, flight.id(), member.id());
          else c.flights.removeCrew(s, flight.id(), member.id());
        });
  }

  public static void status(AppContext c, Session s, Stage owner, Flight flight) {
    Ui.Form f = new Ui.Form(owner, "Update status • " + flight.number());
    ComboBox<FlightStatus> status =
        f.add(
            "Next status",
            s.user().role() == Role.AIR_CREW
                ? Ui.choice(FlightStatus.IN_AIR, FlightStatus.LANDED)
                : Ui.choice(FlightStatus.values()));
    TextField delay = f.add("Total delay minutes", Ui.text("30"));
    TextField reason = f.add("Delay / cancellation reason", new TextField());
    f.note(
        "Current status: "
            + c.flights.get(s, flight.id()).status()
            + ". Boarding needs maintenance clearance and pre-flight sign-off. Departure needs"
            + " completed ground tasks, weight check, all passengers cleared and current ATC"
            + " clearance.");
    f.submit(
        () ->
            c.flights.updateStatus(
                s,
                flight.id(),
                status.getValue(),
                Checks.integer(delay.getText(), "Delay minutes", 0, 1440),
                reason.getText()));
  }

  public static void preflight(AppContext c, Session s, Stage owner, Flight flight) {
    Ui.Form f = new Ui.Form(owner, "Pre-flight sign-off • " + flight.number());
    f.note(
        "Review maintenance release, crew briefing, cabin readiness and operational documents."
            + " Assigned crew and current aircraft clearance are checked by the service.");
    CheckBox checked = f.add("Checklist", new CheckBox("Briefing and pre-flight checks completed"));
    f.submit(() -> c.flights.signPreflight(s, flight.id(), checked.isSelected()));
  }

  public static void weight(AppContext c, Session s, Stage owner, Flight flight) {
    Flight latest = c.flights.get(s, flight.id());
    Ui.Form f = new Ui.Form(owner, "Weight check • " + flight.number());
    f.note(
        c.flights.weightSummary(s, flight.id())
            + "\n"
            + "Classroom load model: empty aircraft + fuel + cargo + 85 kg/passenger + recorded"
            + " baggage. This is a total-weight check, not a real centre-of-gravity calculation.");
    TextField cargo = f.add("Cargo kg", Ui.text("" + latest.cargoKg()));
    TextField fuel = f.add("Fuel kg", Ui.text("" + latest.fuelKg()));
    f.submit(
        () -> {
          double total =
              c.flights.checkWeight(
                  s,
                  flight.id(),
                  Checks.decimal(cargo.getText(), "Cargo", 0, 30000),
                  Checks.decimal(fuel.getText(), "Fuel", 100, 30000));
          Ui.info(
              owner, "Weight accepted", "Estimated takeoff weight: " + Math.round(total) + " kg.");
        });
  }

  public static void manifest(AppContext c, Session s, Stage owner, Flight flight) {
    String text =
        c.flights.manifest(s, flight.id()).stream()
            .map(
                b ->
                    b.reference()
                        + " | "
                        + b.passengerName()
                        + " | "
                        + b.seat()
                        + " | "
                        + b.status()
                        + " | "
                        + b.baggageKg()
                        + " kg | "
                        + b.assistance()
                        + " | "
                        + b.meal()
                        + " | Guardian: "
                        + b.guardianName()
                        + " "
                        + b.guardianPhone())
            .collect(java.util.stream.Collectors.joining("\n"));
    Ui.textWindow(
        owner,
        "Manifest • " + flight.number(),
        text.isBlank() ? "No active passengers yet." : text);
  }

  public static void incident(AppContext c, Session s, Stage owner) {
    Ui.Form f = new Ui.Form(owner, "Report operational / security incident");
    ComboBox<Flight> flight = f.add("Flight", new ComboBox<>());
    Ui.replace(flight, c.flights.all(s));
    ComboBox<String> reason =
        f.add("Reason code", Ui.choice(OperationsService.REASONS.toArray(String[]::new)));
    TextField location = f.add("Location", new TextField());
    DatePicker date = f.add("Incident date", new DatePicker(c.flights.today()));
    TextField time =
        f.add(
            "Time (HH:mm)",
            Ui.text(
                c.clock
                    .instant()
                    .atZone(Formats.ZONE)
                    .toLocalTime()
                    .withSecond(0)
                    .withNano(0)
                    .toString()));
    TextArea desc = f.add("Description", new TextArea());
    desc.setPrefRowCount(3);
    ComboBox<String> severity = f.add("Severity", Ui.choice("LOW", "MEDIUM", "HIGH", "CRITICAL"));
    f.note("High and Critical incidents are escalated to the manager automatically.");
    f.submit(
        () ->
            c.operations.reportIncident(
                s,
                Checks.required(flight.getValue(), "a flight").id(),
                reason.getValue(),
                location.getText(),
                Formats.timestamp(date.getValue(), time.getText()),
                desc.getText(),
                severity.getValue()));
  }

  public static void exportText(Stage owner, String filename, String content) {
    FileChooser chooser = new FileChooser();
    chooser.setInitialFileName(filename);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text file", "*.txt"));
    java.io.File file = chooser.showSaveDialog(owner);
    if (file == null) return;
    try {
      Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
      Ui.info(owner, "Exported", file.getAbsolutePath());
    } catch (java.io.IOException e) {
      throw new com.aircampus.exception.AppException("Could not write the export file.", e);
    }
  }
}
