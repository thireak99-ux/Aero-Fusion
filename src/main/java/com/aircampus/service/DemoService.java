package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.*;
import java.util.*;

/** Fresh, isolated training schedules. Existing flights and bookings are never rewritten. */
public final class DemoService extends ServiceSupport {
  public enum TimePoint {
    BOOKING("48h before departure: full refund", -172800, false),
    CHECK_IN_OPENS("24h before departure: check-in opens", -86400, false),
    WORKFLOW("2h before departure: full workflow", -7200, false),
    CHECK_IN_CUTOFF("60m before departure: check-in cutoff", -3600, false),
    CHECK_IN_CLOSED("30m before departure: check-in closed", -1800, false),
    TAKEOFF("10m before departure: takeoff", -600, false),
    LANDING("10m before arrival: landing", -600, true);
    private final String label;
    private final long seconds;
    private final boolean arrival;

    TimePoint(String label, long seconds, boolean arrival) {
      this.label = label;
      this.seconds = seconds;
      this.arrival = arrival;
    }

    long timestamp(Flight f) {
      return (arrival ? f.estimatedArrival() : f.estimatedDeparture()) + seconds;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private final DemoClock demoClock;
  private final FlightService flightService;
  private final boolean enabled;
  private long lastMinute = Long.MIN_VALUE;

  public DemoService(
      Database db,
      AuthService auth,
      DemoClock clock,
      EventBus events,
      FlightService flights,
      boolean enabled) {
    super(db, auth, clock, events);
    demoClock = clock;
    flightService = flights;
    this.enabled = enabled;
  }

  /**
   * Called at startup and from dashboard refresh. Automatic refill pauses during time experiments.
   */
  public synchronized int ensureAvailable() {
    if (!enabled || demoClock.isFrozen() || lastMinute == now() / 60) return 0;
    lastMinute = now() / 60;
    int count =
        db.transaction(
            () -> {
              long usable =
                  db.count(
                      "SELECT COUNT(*) FROM flights f JOIN aircraft a ON"
                          + " a.id=f.aircraft_id WHERE f.status IN"
                          + " ('SCHEDULED','DELAYED','BOARDING') AND"
                          + " f.departure+f.delay_minutes*60>? AND"
                          + " f.departure+f.delay_minutes*60<=? AND (SELECT"
                          + " COUNT(*) FROM bookings b WHERE b.flight_id=f.id"
                          + " AND b.status<>'CANCELLED')<a.rows*6",
                      now() + 3600,
                      now() + 86400);
              if (usable > 0) return 0;
              List<Flight> created = createWeek();
              db.update(
                  "INSERT INTO audit(created_at,actor,action,details)"
                      + " VALUES(?,'System','DEMO_WEEK_ADDED',?)",
                  now(),
                  created.size() + " current flights; prior records retained");
              return created.size();
            });
    if (count > 0)
      events.publish(new AirportEvent("DEMO_WEEK_ADDED", 0, "Fresh demo flights available"));
    return count;
  }

  public List<Flight> newWeek(Session s) {
    auth.require(s, Role.MANAGER);
    return change(s, "FRESH_DEMO_WEEK", 0, this::createWeek);
  }

  private String nextFlightNumber() {
    int candidate =
        db
            .query(
                "SELECT value FROM settings WHERE key='demo_next_number'",
                r -> Integer.parseInt(r.getString(1)))
            .stream()
            .findFirst()
            .orElse(101);
    for (int attempt = 0; attempt < 9899; attempt++) {
      if (candidate > 9999) candidate = 101;
      String number = "AF" + candidate++;
      if (db.count("SELECT COUNT(*) FROM flights WHERE number=?", number) == 0) {
        db.update(
            "INSERT INTO settings(key,value) VALUES('demo_next_number',?) ON"
                + " CONFLICT(key) DO UPDATE SET value=excluded.value",
            String.valueOf(candidate));
        return number;
      }
    }
    throw new com.aircampus.exception.ValidationException(
        "All demo flight numbers are in use. Use a separate demonstration database.");
  }

  private List<Flight> createWeek() {
    boolean initial = db.count("SELECT COUNT(*) FROM flights") == 0;
    long base = now() / 60 * 60;
    db.update("INSERT INTO demo_batches(created_at) VALUES(?)", now());
    long batch = db.count("SELECT last_insert_rowid()");
    long[] aircraftIds = new long[4];
    long[][] crewIds = new long[4][3];
    for (int route = 0; route < 4; route++) {
      if (initial
          && db.count(
                  "SELECT COUNT(*) FROM aircraft WHERE id=? AND registration=?",
                  route + 1,
                  "DEMO-" + (route + 1))
              == 1) aircraftIds[route] = route + 1;
      else {
        String registration = "AF-DEMO-" + batch + "-" + (route + 1);
        db.update(
            "INSERT INTO"
                + " aircraft(registration,type,rows,empty_kg,max_takeoff_kg,airworthy)"
                + " VALUES(?,'A320',12,42000,77000,0)",
            registration);
        aircraftIds[route] = db.count("SELECT last_insert_rowid()");
      }
      for (int duty = 0; duty < 3; duty++) {
        long original = route * 3L + duty + 1;
        if (initial && db.count("SELECT COUNT(*) FROM crew WHERE id=?", original) == 1)
          crewIds[route][duty] = original;
        else {
          String title =
              switch (duty) {
                case 0 -> "PILOT";
                case 1 -> "CO_PILOT";
                default -> "CABIN";
              };
          db.update(
              "INSERT INTO crew(name,duty,certified_until,aircraft_type)" + " VALUES(?,?,?,'A320')",
              "Demo " + batch + " / team " + (route + 1) + " / " + title,
              title,
              base + 365L * 86400);
          crewIds[route][duty] = db.count("SELECT last_insert_rowid()");
        }
      }
    }
    Airport[] destinations = {Airport.BKK, Airport.SIN, Airport.SAI, Airport.KUL};
    List<Flight> created = new ArrayList<>();
    for (int day = 0; day < 7; day++)
      for (int route = 0; route < 4; route++) {
        long departure = base + 14400 + day * 86400L + route * 5400;
        long arrival = departure + (route == 2 ? 3600 : 7200);
        String gate = "";
        for (String candidate : FlightService.GATES) {
          try {
            flightService.validateAllocation(
                0, aircraftIds[route], candidate, Airport.KTI, departure, arrival);
            gate = candidate;
            break;
          } catch (com.aircampus.exception.ConflictException occupied) {
            /* Try another valid gate. */
          }
        }
        flightService.validateAllocation(
            0, aircraftIds[route], gate, Airport.KTI, departure, arrival);
        String number = nextFlightNumber();
        String callsign = "DEMO" + number.substring(2);
        db.update(
            "INSERT INTO"
                + " flights(number,origin,destination,departure,arrival,price_cents,aircraft_id,gate,callsign)"
                + " VALUES(?,?,?,?,?,?,?,?,?)",
            number,
            Airport.KTI,
            destinations[route],
            departure,
            arrival,
            4500 + route * 3500,
            aircraftIds[route],
            gate,
            callsign);
        long id = db.count("SELECT last_insert_rowid()");
        for (String task : FlightService.TASKS)
          db.update("INSERT INTO ground_tasks(flight_id,name) VALUES(?,?)", id, task);
        for (long crewId : crewIds[route])
          db.update("INSERT INTO flight_crew VALUES(?,?)", id, crewId);
        db.update("INSERT INTO demo_flights(flight_id,batch_id) VALUES(?,?)", id, batch);
        created.add(flight(id));
      }
    return List.copyOf(created);
  }

  private void changeClock(Session s, String action, Runnable operation) {
    auth.require(s, Role.MANAGER);
    db.transaction(
        () -> {
          // Leases and time-sensitive clearances must be re-established after a time
          // jump.
          db.update("DELETE FROM counters");
          db.update("UPDATE runway_slots SET state='ASSIGNED' WHERE state='CLEARED'");
          audit(s, action, "Demo time control; counters closed and active clearances revoked");
          return null;
        });
    operation.run();
    lastMinute = Long.MIN_VALUE;
    events.publish(new AirportEvent("DEMO_TIME_CHANGED", 0, demoClock.label()));
  }

  public void freezeNow(Session s) {
    Instant point = clock.instant();
    changeClock(s, "FREEZE_DEMO_TIME", () -> demoClock.freeze(point));
  }

  public void useRealTime(Session s) {
    changeClock(s, "USE_COMPUTER_TIME", demoClock::useRealTime);
    ensureAvailable();
  }

  public void setTime(Session s, Instant point) {
    auth.require(s, Role.MANAGER);
    Checks.required(point, "demo date and time");
    int year = point.atZone(Formats.ZONE).getYear();
    Checks.that(year >= 2000 && year <= 2099, "Choose a demonstration date from 2000 to 2099.");
    changeClock(s, "SET_DEMO_TIME", () -> demoClock.freeze(point));
  }

  public void useFlightPoint(Session s, long flightId, TimePoint point) {
    auth.require(s, Role.MANAGER);
    Checks.required(point, "time preset");
    Flight f = flight(flightId);
    setTime(s, Instant.ofEpochSecond(point.timestamp(f)));
  }
}
