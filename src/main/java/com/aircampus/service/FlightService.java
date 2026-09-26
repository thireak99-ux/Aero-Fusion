package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.*;
import java.util.*;

public final class FlightService extends ServiceSupport {
  public static final List<String> GATES = List.of("G1", "G2", "G3", "G4", "G5", "G6");
  public static final List<String> TASKS =
      List.of("Baggage unloading", "Cleaning", "Catering", "Fueling", "Baggage loading");

  public FlightService(Database db, AuthService auth, Clock clock, EventBus events) {
    super(db, auth, clock, events);
  }

  public List<Flight> all(Session s) {
    auth.require(s);
    return flights.findAll();
  }

  public Flight get(Session s, long id) {
    auth.require(s);
    return flight(id);
  }

  public List<Aircraft> aircrafts(Session s) {
    auth.require(s);
    return db.query("SELECT * FROM aircraft ORDER BY id", Mappers.AIRCRAFT);
  }

  public Aircraft getAircraft(Session s, long id) {
    auth.require(s);
    return aircraft(id);
  }

  public List<Flight> search(Session s, Airport origin, Airport destination, LocalDate date) {
    auth.require(s);
    Checks.required(origin, "origin");
    Checks.required(destination, "destination");
    Checks.required(date, "travel date");
    Checks.that(origin != destination, "Origin and destination must differ.");
    Checks.that(!date.isBefore(today()), "Travel date cannot be in the past.");
    return flights.findAll().stream()
        .filter(
            f ->
                f.origin() == origin
                    && f.destination() == destination
                    && Formats.date(f.departure()).equals(date)
                    && f.status().isOpen()
                    && f.estimatedDeparture() > now() + 3600)
        .toList();
  }

  public List<CrewMember> crew(Session s) {
    auth.require(s, Role.OPS, Role.AIR_CREW, Role.MANAGER);
    return db.query("SELECT * FROM crew ORDER BY duty,name", Mappers.CREW);
  }

  public List<CrewMember> assignedCrew(Session s, long id) {
    auth.require(s, Role.OPS, Role.AIR_CREW, Role.MANAGER);
    flight(id);
    return crewFor(id);
  }

  private List<CrewMember> crewFor(long id) {
    return db.query(
        "SELECT c.* FROM crew c JOIN flight_crew fc ON c.id=fc.crew_id WHERE fc.flight_id=? ORDER"
            + " BY duty",
        Mappers.CREW,
        id);
  }

  public List<Booking> manifest(Session s, long id) {
    auth.require(s, Role.OPS, Role.AIR_CREW, Role.CHECK_IN, Role.MANAGER);
    flight(id);
    return bookings.manifest(id);
  }

  void validateAllocation(long exclude, long aid, String gate, Airport origin, long dep, long arr) {
    aircraft(aid);
    for (Flight other : flights.findAll()) {
      if (other.id() == exclude || other.status() == FlightStatus.CANCELLED) continue;
      if (other.aircraftId() == aid
          && dep < other.estimatedArrival() + 2700
          && arr + 2700 > other.estimatedDeparture())
        throw new ConflictException(
            "Aircraft overlaps " + other.number() + " (including 45-minute turnaround).");
      if (!gate.isBlank()
          && other.gate().equals(gate)
          && other.origin() == origin
          && Math.abs(dep - other.estimatedDeparture()) < 5400)
        throw new ConflictException(
            "Gate "
                + gate
                + " overlaps "
                + other.number()
                + ". Allow 90 minutes between departures.");
    }
  }

  private void validateCrew(CrewMember c, long exclude, long aid, long dep, long arr) {
    Checks.that(c.certifiedUntil() >= arr, "Crew certification expires before flight completion.");
    Checks.that(
        c.aircraftType().equals(aircraft(aid).type()),
        "Crew certification does not match the aircraft type.");
    for (Flight f :
        db.query(
            "SELECT f.* FROM flights f JOIN flight_crew fc ON f.id=fc.flight_id WHERE fc.crew_id=?"
                + " AND f.id<>? AND f.status<>'CANCELLED'",
            Mappers.FLIGHT,
            c.id(),
            exclude))
      Checks.that(
          dep >= f.estimatedArrival() + 28800 || arr + 28800 <= f.estimatedDeparture(),
          c.name() + " needs 8 hours of rest between flights; conflict with " + f.number() + ".");
  }

  public void create(Session s, FlightDraft d) {
    auth.require(s, Role.OPS);
    change(
        s,
        "CREATE_FLIGHT",
        0,
        () -> {
          Checks.required(d, "schedule").validate();
          Checks.that(
              d.departure() > now() + 3600, "New flights must depart more than one hour from now.");
          validateAllocation(0, d.aircraftId(), "", d.origin(), d.departure(), d.arrival());
          db.update(
              "INSERT INTO"
                  + " flights(number,origin,destination,departure,arrival,price_cents,aircraft_id,callsign,icao24)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              d.number(),
              d.origin(),
              d.destination(),
              d.departure(),
              d.arrival(),
              d.priceCents(),
              d.aircraftId(),
              d.callsign(),
              d.icao24());
          long id = db.count("SELECT id FROM flights WHERE number=?", d.number());
          for (String task : TASKS)
            db.update("INSERT INTO ground_tasks(flight_id,name) VALUES(?,?)", id, task);
          return null;
        });
  }

  public void edit(Session s, long id, FlightDraft d) {
    auth.require(s, Role.OPS);
    change(
        s,
        "EDIT_SCHEDULE",
        id,
        () -> {
          Flight f = flight(id);
          Checks.that(
              f.status() == FlightStatus.SCHEDULED || f.status() == FlightStatus.DELAYED,
              "Only scheduled or delayed flights can be edited.");
          Checks.required(d, "schedule").validate();
          Checks.that(
              d.departure() > now() + 3600, "Departure must be more than one hour from now.");
          if (!bookings.manifest(id).isEmpty())
            Checks.that(
                f.origin() == d.origin()
                    && f.destination() == d.destination()
                    && f.aircraftId() == d.aircraftId(),
                "A booked flight must retain its route and aircraft; create an alternative flight"
                    + " for rescheduling.");
          validateAllocation(id, d.aircraftId(), f.gate(), d.origin(), d.departure(), d.arrival());
          for (CrewMember c : crewFor(id))
            validateCrew(c, id, d.aircraftId(), d.departure(), d.arrival());
          db.update(
              "UPDATE flights SET"
                  + " number=?,origin=?,destination=?,departure=?,arrival=?,delay_minutes=0,price_cents=?,aircraft_id=?,callsign=?,icao24=?,preflight=0,weight_checked=0,pushback=0,status='SCHEDULED'"
                  + " WHERE id=?",
              d.number(),
              d.origin(),
              d.destination(),
              d.departure(),
              d.arrival(),
              d.priceCents(),
              d.aircraftId(),
              d.callsign(),
              d.icao24(),
              id);
          db.update("DELETE FROM runway_slots WHERE flight_id=?", id);
          notifyFlight(id, d.number() + ": schedule changed. View your updated trip.");
          return null;
        });
  }

  public void assignGate(Session s, long id, String gate) {
    auth.require(s, Role.OPS);
    change(
        s,
        "GATE_CHANGED",
        id,
        () -> {
          Flight f = flight(id);
          mutableFlight(f);
          Checks.that(GATES.contains(gate), "Choose an available gate.");
          validateAllocation(
              id, f.aircraftId(), gate, f.origin(), f.estimatedDeparture(), f.estimatedArrival());
          db.update("UPDATE flights SET gate=?,pushback=0 WHERE id=?", gate, id);
          db.update("DELETE FROM runway_slots WHERE flight_id=? AND operation='TAKEOFF'", id);
          notifyFlight(id, f.number() + ": proceed to gate " + gate + ".");
          return null;
        });
  }

  public void assignCrew(Session s, long id, long crewId) {
    auth.require(s, Role.OPS);
    change(
        s,
        "ASSIGN_CREW",
        id,
        () -> {
          Flight f = flight(id);
          mutableFlight(f);
          CrewMember c =
              db.query("SELECT * FROM crew WHERE id=?", Mappers.CREW, crewId).stream()
                  .findFirst()
                  .orElseThrow(() -> new ValidationException("Select a crew member."));
          validateCrew(c, id, f.aircraftId(), f.estimatedDeparture(), f.estimatedArrival());
          Checks.that(
              crewFor(id).stream().noneMatch(existing -> existing.duty().equals(c.duty())),
              "This duty is already assigned. Remove the existing assignment first.");
          db.update("INSERT INTO flight_crew VALUES(?,?)", id, crewId);
          db.update("UPDATE flights SET preflight=0 WHERE id=?", id);
          invalidateLoad(id);
          return null;
        });
  }

  public void removeCrew(Session s, long id, long crewId) {
    auth.require(s, Role.OPS);
    change(
        s,
        "REMOVE_CREW",
        id,
        () -> {
          mutableFlight(flight(id));
          Checks.that(
              db.update("DELETE FROM flight_crew WHERE flight_id=? AND crew_id=?", id, crewId) > 0,
              "That crew member is not assigned.");
          db.update("UPDATE flights SET preflight=0 WHERE id=?", id);
          invalidateLoad(id);
          return null;
        });
  }

  private void crewReady(Flight f) {
    List<CrewMember> crew = crewFor(f.id());
    Checks.that(
        crew.stream()
            .map(CrewMember::duty)
            .collect(java.util.stream.Collectors.toSet())
            .containsAll(List.of("PILOT", "CO_PILOT", "CABIN")),
        "Assign a pilot, co-pilot and cabin crew member first.");
    for (CrewMember c : crew)
      validateCrew(c, f.id(), f.aircraftId(), f.estimatedDeparture(), f.estimatedArrival());
  }

  public void signPreflight(Session s, long id, boolean checklistConfirmed) {
    auth.require(s, Role.OPS, Role.AIR_CREW);
    change(
        s,
        "PREFLIGHT_SIGNED",
        id,
        () -> {
          Flight f = flight(id);
          mutableFlight(f);
          Checks.that(checklistConfirmed, "Confirm the pre-flight briefing checklist.");
          Checks.that(
              aircraft(f.aircraftId()).airworthy(), "Maintenance must clear the aircraft first.");
          crewReady(f);
          db.update("UPDATE flights SET preflight=1 WHERE id=?", id);
          return null;
        });
  }

  double totalWeight(Flight f) {
    return aircraft(f.aircraftId()).emptyKg()
        + f.fuelKg()
        + f.cargoKg()
        + bookings.manifest(f.id()).stream().mapToDouble(b -> 85 + b.baggageKg()).sum();
  }

  public String weightSummary(Session s, long id) {
    auth.require(s);
    Flight f = flight(id);
    return String.format(
        Locale.US,
        "Estimated takeoff weight: %.0f / %.0f kg (85 kg per passenger)",
        totalWeight(f),
        aircraft(f.aircraftId()).maxTakeoffKg());
  }

  public double checkWeight(Session s, long id, double cargo, double fuel) {
    auth.require(s, Role.OPS, Role.AIR_CREW);
    return change(
        s,
        "WEIGHT_CHECK",
        id,
        () -> {
          Flight f = flight(id);
          mutableFlight(f);
          Checks.range(cargo, "Cargo kg", 0, 30000);
          Checks.range(fuel, "Fuel kg", 100, 30000);
          double total =
              aircraft(f.aircraftId()).emptyKg()
                  + cargo
                  + fuel
                  + bookings.manifest(id).stream().mapToDouble(b -> 85 + b.baggageKg()).sum();
          Checks.that(
              total <= aircraft(f.aircraftId()).maxTakeoffKg(),
              "Aircraft is overweight: " + Math.round(total) + " kg. Reduce cargo / load.");
          invalidateLoad(id);
          db.update(
              "UPDATE flights SET cargo_kg=?,fuel_kg=?,weight_checked=1 WHERE id=?",
              cargo,
              fuel,
              id);
          return total;
        });
  }

  void departureReady(Flight f) {
    Checks.that(
        aircraft(f.aircraftId()).airworthy() && f.preflight(),
        "Maintenance and pre-flight sign-off are required.");
    crewReady(f);
    Checks.that(
        f.weightChecked() && totalWeight(f) <= aircraft(f.aircraftId()).maxTakeoffKg(),
        "Recheck weight after the latest passenger / baggage change.");
    Checks.that(!f.gate().isBlank(), "Assign a gate first.");
    Checks.that(
        db.count("SELECT COUNT(*) FROM ground_tasks WHERE flight_id=? AND status='DONE'", f.id())
            == TASKS.size(),
        "Complete all ground tasks.");
    Checks.that(f.pushback(), "Ground crew must request pushback.");
    Checks.that(
        bookings.manifest(f.id()).stream().allMatch(b -> b.checkedIn() && b.gateCleared()),
        "All active passengers need check-in and security gate clearance.");
  }

  public void updateStatus(Session s, long id, FlightStatus next, int delay, String reason) {
    auth.require(s, Role.OPS, Role.AIR_CREW);
    change(
        s,
        "STATUS_CHANGED",
        id,
        () -> {
          Flight f = flight(id);
          Checks.required(next, "flight status");
          if (s.user().role() == Role.AIR_CREW)
            Checks.that(
                next == FlightStatus.IN_AIR || next == FlightStatus.LANDED,
                "Aircrew may record In Air or Landed. Operations controls other statuses.");
          Checks.that(next != f.status(), "Flight already has this status.");
          boolean allowed =
              switch (f.status()) {
                case SCHEDULED ->
                    next == FlightStatus.BOARDING
                        || next == FlightStatus.DELAYED
                        || next == FlightStatus.CANCELLED;
                case DELAYED ->
                    next == FlightStatus.SCHEDULED
                        || next == FlightStatus.BOARDING
                        || next == FlightStatus.CANCELLED;
                case BOARDING ->
                    next == FlightStatus.DEPARTED
                        || next == FlightStatus.DELAYED
                        || next == FlightStatus.CANCELLED;
                case DEPARTED -> next == FlightStatus.IN_AIR;
                case IN_AIR -> next == FlightStatus.LANDED;
                default -> false;
              };
          Checks.that(
              allowed, "Invalid flight-status transition: " + f.status() + " → " + next + ".");
          if (next == FlightStatus.BOARDING) {
            Checks.that(
                aircraft(f.aircraftId()).airworthy() && f.preflight(),
                "Maintenance clearance and pre-flight sign-off are required before boarding.");
            crewReady(f);
            Checks.that(!f.gate().isBlank(), "Assign a gate before boarding.");
            Checks.that(
                f.estimatedDeparture() > now(),
                "Departure time is in the past. Update the schedule.");
          }
          if (next == FlightStatus.DEPARTED) {
            departureReady(f);
            Checks.that(
                db.count(
                        "SELECT COUNT(*) FROM runway_slots WHERE flight_id=? AND"
                            + " operation='TAKEOFF' AND state='CLEARED' AND start_time<=? AND"
                            + " end_time>?",
                        id,
                        now(),
                        now())
                    == 1,
                "ATC takeoff clearance must be active now.");
            Checks.that(
                db.query("SELECT value FROM settings WHERE key='weather'", r -> r.getString(1))
                    .get(0)
                    .equals("CLEAR"),
                "Weather does not permit departure.");
          }
          if (next == FlightStatus.LANDED)
            Checks.that(
                db.count(
                        "SELECT COUNT(*) FROM runway_slots WHERE flight_id=? AND"
                            + " operation='LANDING' AND state='CLEARED' AND start_time<=? AND"
                            + " end_time>?",
                        id,
                        now(),
                        now())
                    == 1,
                "ATC landing clearance must be active now.");
          if (next == FlightStatus.DELAYED || next == FlightStatus.SCHEDULED) {
            int minutes = next == FlightStatus.DELAYED ? delay : 0;
            Checks.that(
                minutes >= 0
                    && minutes <= 1440
                    && (next != FlightStatus.DELAYED || minutes > f.delayMinutes()),
                "A delay must increase the total delay, up to 1,440 minutes.");
            Checks.text(reason, "Reason", 200);
            long dep = f.departure() + minutes * 60L, arr = f.arrival() + minutes * 60L;
            Checks.that(dep > now(), "The new estimated departure must be in the future.");
            validateAllocation(id, f.aircraftId(), f.gate(), f.origin(), dep, arr);
            for (CrewMember c : crewFor(id)) validateCrew(c, id, f.aircraftId(), dep, arr);
            db.update("UPDATE flights SET delay_minutes=?,pushback=0 WHERE id=?", minutes, id);
            db.update("DELETE FROM runway_slots WHERE flight_id=?", id);
          }
          if (next == FlightStatus.CANCELLED) {
            Checks.text(reason, "Cancellation reason", 200);
            for (Booking b : bookings.manifest(id))
              db.update(
                  "UPDATE bookings SET status='CANCELLED',gate_cleared=0,refund_cents=? WHERE"
                      + " reference=?",
                  b.paidCents() + b.baggageFeeCents(),
                  b.reference());
            db.update("DELETE FROM counters WHERE flight_id=?", id);
          }
          if (next == FlightStatus.DEPARTED
              || next == FlightStatus.LANDED
              || next == FlightStatus.CANCELLED)
            db.update("DELETE FROM runway_slots WHERE flight_id=?", id);
          db.update("UPDATE flights SET status=? WHERE id=?", next, id);
          notifyFlight(
              id,
              f.number()
                  + ": "
                  + next
                  + (next == FlightStatus.DELAYED ? " (" + delay + " minutes)" : "")
                  + (next == FlightStatus.CANCELLED
                      ? ". Boarding passes invalid; simulated full refunds issued."
                      : ""));
          return null;
        });
  }
}
