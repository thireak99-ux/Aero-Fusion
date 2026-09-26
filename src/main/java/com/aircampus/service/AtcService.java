package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.Clock;
import java.util.*;

public final class AtcService extends ServiceSupport {
  private final FlightService flightService;
  public static final List<String> RUNWAYS = List.of("RWY 01", "RWY 19");

  // Two directions of ONE physical runway: all allocations share the same conflict check.
  public AtcService(
      Database db, AuthService auth, Clock clock, EventBus events, FlightService flightService) {
    super(db, auth, clock, events);
    this.flightService = flightService;
  }

  public String weather(Session s) {
    auth.require(s);
    return db.query("SELECT value FROM settings WHERE key='weather'", r -> r.getString(1)).get(0);
  }

  public void weather(Session s, String value) {
    auth.require(s, Role.ATC, Role.MANAGER);
    change(
        s,
        "WEATHER_CHANGED",
        0,
        () -> {
          Checks.that(
              List.of("CLEAR", "LOW_VISIBILITY", "STORM").contains(value),
              "Choose a weather condition.");
          db.update("UPDATE settings SET value=? WHERE key='weather'", value);
          if (!value.equals("CLEAR"))
            db.update("UPDATE runway_slots SET state='ASSIGNED' WHERE state='CLEARED'");
          return null;
        });
  }

  public List<RunwaySlot> queue(Session s) {
    auth.require(s, Role.ATC, Role.MANAGER);
    return db.query(
        "SELECT r.* FROM runway_slots r JOIN flights f ON f.id=r.flight_id ORDER BY f.emergency"
            + " DESC,r.queued_at,r.id",
        Mappers.SLOT);
  }

  public void enqueue(Session s, long id, String operation) {
    auth.require(s, Role.ATC);
    change(
        s,
        "ATC_QUEUE",
        id,
        () -> {
          Flight f = flight(id);
          Checks.that(
              List.of("TAKEOFF", "LANDING").contains(operation), "Select takeoff or landing.");
          Checks.that(
              operation.equals("TAKEOFF") ? f.status().isOpen() : f.status() == FlightStatus.IN_AIR,
              "Flight status does not match this operation.");
          Checks.that(
              db.count("SELECT COUNT(*) FROM runway_slots WHERE flight_id=?", id) == 0,
              "Flight already has a queue entry. Use Hold / Release to change it.");
          db.update(
              "INSERT INTO runway_slots(flight_id,operation,queued_at) VALUES(?,?,?)",
              id,
              operation,
              now());
          return null;
        });
  }

  private RunwaySlot slot(long id) {
    return db.query("SELECT * FROM runway_slots WHERE flight_id=?", Mappers.SLOT, id).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new com.aircampus.exception.ValidationException(
                    "Add the flight to the ATC queue first."));
  }

  public void assignRunway(Session s, long id, String runway, long start) {
    auth.require(s, Role.ATC);
    change(
        s,
        "RUNWAY_ASSIGNED",
        id,
        () -> {
          RunwaySlot slot = slot(id);
          Checks.that(
              !slot.state().equals("CLEARED"),
              "Release the current clearance before changing runway allocation.");
          Checks.that(RUNWAYS.contains(runway), "Select a runway direction.");
          Checks.that(
              start >= now() - 60 && start <= now() + 172800,
              "Runway slot must begin now or within the next 48 hours.");
          long end = start + 600;
          Checks.that(
              db.count(
                      "SELECT COUNT(*) FROM runway_slots WHERE flight_id<>? AND runway<>'' AND"
                          + " start_time<? AND end_time>?",
                      id,
                      end,
                      start)
                  == 0,
              "Runway conflict: RWY 01 and RWY 19 are opposite directions of the same physical"
                  + " runway.");
          db.update(
              "UPDATE runway_slots SET runway=?,start_time=?,end_time=?,state='ASSIGNED' WHERE"
                  + " flight_id=?",
              runway,
              start,
              end,
              id);
          return null;
        });
  }

  public void hold(Session s, long id, String reason) {
    auth.require(s, Role.ATC);
    change(
        s,
        "HOLDING_PATTERN",
        id,
        () -> {
          Flight f = flight(id);
          Checks.that(
              f.status().isOpen() || f.status() == FlightStatus.IN_AIR,
              "This flight cannot be held.");
          slot(id);
          Checks.text(reason, "Hold reason", 200);
          db.update(
              "UPDATE runway_slots SET state='HOLDING',runway='',start_time=0,end_time=0 WHERE"
                  + " flight_id=?",
              id);
          notifyFlight(id, f.number() + ": holding — " + reason);
          return null;
        });
  }

  public void emergency(Session s, long id, String reason) {
    auth.require(s, Role.ATC);
    change(
        s,
        "EMERGENCY_DECLARED",
        id,
        () -> {
          Flight f = flight(id);
          Checks.that(
              f.status().isOpen() || f.status() == FlightStatus.IN_AIR,
              "Only active flights can declare an emergency.");
          slot(id);
          Checks.text(reason, "Emergency reason", 200);
          db.update("UPDATE flights SET emergency=1 WHERE id=?", id);
          audit(s, "EMERGENCY_REASON", f.number() + ": " + reason);
          return null;
        });
  }

  public void release(Session s, long id) {
    auth.require(s, Role.ATC);
    change(
        s,
        "RUNWAY_RELEASED",
        id,
        () -> {
          Checks.that(
              db.update("DELETE FROM runway_slots WHERE flight_id=?", id) > 0,
              "Flight has no runway or queue assignment.");
          db.update("UPDATE flights SET emergency=0 WHERE id=?", id);
          return null;
        });
  }

  public void grant(Session s, long id) {
    auth.require(s, Role.ATC);
    change(
        s,
        "CLEARANCE_GRANTED",
        id,
        () -> {
          Flight f = flight(id);
          RunwaySlot slot = slot(id);
          Checks.that(
              slot.state().equals("ASSIGNED"), "Assign a runway before granting clearance.");
          Checks.that(
              slot.startTime() <= now() && slot.endTime() > now(),
              "Clearance is only allowed during the reserved 10-minute slot.");
          Checks.that(weather(s).equals("CLEAR"), "Weather is not acceptable. Hold the flight.");
          Checks.that(
              db.count(
                      "SELECT COUNT(*) FROM runway_slots WHERE flight_id<>? AND runway<>'' AND"
                          + " start_time<=? AND end_time>?",
                      id,
                      now(),
                      now())
                  == 0,
              "Another aircraft occupies this runway / final approach.");
          List<RunwaySlot> eligible =
              queue(s).stream()
                  .filter(
                      x ->
                          !x.state().equals("HOLDING")
                              && (x.startTime() == 0
                                  || (x.startTime() <= now() && x.endTime() > now())))
                  .toList();
          Checks.that(
              eligible.isEmpty() || eligible.get(0).flightId() == id,
              "Earlier traffic has priority. Sequence it first, or put it on hold with a reason."
                  + " Emergency flights have priority.");
          if (slot.operation().equals("TAKEOFF")) {
            Checks.that(
                f.status() == FlightStatus.BOARDING,
                "Flight must be Boarding before takeoff clearance.");
            flightService.departureReady(f);
          } else
            Checks.that(
                f.status() == FlightStatus.IN_AIR, "Landing clearance requires an in-air flight.");
          db.update("UPDATE runway_slots SET state='CLEARED' WHERE flight_id=?", id);
          return null;
        });
  }
}
