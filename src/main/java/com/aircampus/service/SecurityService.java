package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.Clock;
import java.util.*;

public final class SecurityService extends ServiceSupport {
  public SecurityService(Database db, AuthService auth, Clock clock, EventBus events) {
    super(db, auth, clock, events);
  }

  public List<Booking> queue(Session s) {
    auth.require(s, Role.SECURITY);
    return db.query(
        "SELECT b.* FROM bookings b JOIN flights f ON f.id=b.flight_id WHERE b.status='CHECKED_IN'"
            + " AND f.status IN ('SCHEDULED','DELAYED','BOARDING') ORDER BY"
            + " b.gate_cleared,b.reference",
        Mappers.BOOKING);
  }

  public boolean watchlisted(Session s, String name) {
    auth.require(s, Role.SECURITY, Role.MANAGER);
    return db.count("SELECT COUNT(*) FROM watchlist WHERE name=?", Checks.normalizedName(name)) > 0;
  }

  public List<String> watchlist(Session s) {
    auth.require(s, Role.SECURITY, Role.MANAGER);
    return db.query(
        "SELECT name,reason FROM watchlist ORDER BY name",
        r -> r.getString(1) + " | " + r.getString(2));
  }

  public void addWatchlist(Session s, String name, String reason) {
    auth.require(s, Role.MANAGER);
    change(
        s,
        "WATCHLIST_ADD",
        0,
        () -> {
          db.update(
              "INSERT INTO watchlist(name,reason) VALUES(?,?)",
              Checks.normalizedName(name),
              Checks.text(reason, "Watchlist reason", 200));
          for (Booking b : bookings.findAll())
            if (Checks.normalizedName(b.passengerName()).equals(Checks.normalizedName(name))) {
              db.update("UPDATE bookings SET gate_cleared=0 WHERE reference=?", b.reference());
              invalidateLoad(b.flightId());
            }
          return null;
        });
  }

  public void removeWatchlist(Session s, String name) {
    auth.require(s, Role.MANAGER);
    change(
        s,
        "WATCHLIST_REMOVE",
        0,
        () -> {
          Checks.that(
              db.update("DELETE FROM watchlist WHERE name=?", Checks.normalizedName(name)) > 0,
              "Name is not on the watchlist.");
          return null;
        });
  }

  private Booking screenable(String ref) {
    Booking b = booking(ref);
    active(b);
    Checks.that(b.checkedIn(), "Passenger must check in before screening.");
    mutableFlight(flight(b.flightId()));
    return b;
  }

  public void screen(Session s, String ref, boolean baggage, String result, String items) {
    auth.require(s, Role.SECURITY);
    change(
        s,
        baggage ? "BAGGAGE_SCREEN" : "PASSENGER_SCREEN",
        0,
        () -> {
          Booking b = screenable(ref);
          Checks.that(List.of("PASS", "FAIL").contains(result), "Select PASS or FAIL.");
          String note = Checks.text(items, "Screening note / items (enter None when clear)", 200);
          if (result.equals("PASS"))
            Checks.that(
                note.equalsIgnoreCase("None"),
                "A PASS must have no prohibited or suspicious items. Select FAIL and describe the"
                    + " items.");
          if (result.equals("FAIL"))
            Checks.that(
                !note.equalsIgnoreCase("None"), "Describe the reason for the failed screening.");
          db.update(
              baggage
                  ? "UPDATE bookings SET baggage_screen=?,gate_cleared=0 WHERE reference=?"
                  : "UPDATE bookings SET passenger_screen=?,gate_cleared=0 WHERE reference=?",
              result,
              b.reference());
          invalidateLoad(b.flightId());
          audit(
              s,
              "SCREENING_RESULT",
              b.reference()
                  + " / "
                  + (baggage ? "Baggage" : "Passenger")
                  + " / "
                  + result
                  + " / "
                  + note);
          return null;
        });
  }

  public void gateClearance(Session s, String ref, String idName, String passport) {
    auth.require(s, Role.SECURITY);
    change(
        s,
        "GATE_CLEARANCE",
        0,
        () -> {
          Booking b = screenable(ref);
          Checks.that(
              Checks.normalizedName(idName).equals(Checks.normalizedName(b.passengerName()))
                  && Checks.passport(passport).equals(b.passport()),
              "Photo ID does not match the boarding pass.");
          if (watchlisted(s, b.passengerName()))
            throw new SecurityCheckException(
                "Passenger is on the watchlist. Escalate the incident; clearance denied.");
          Checks.that(
              b.passengerScreen().equals("PASS") && b.baggageScreen().equals("PASS"),
              "Passenger and baggage screening must both pass.");
          Checks.that(!b.gateCleared(), "Passenger already has gate clearance.");
          db.update(
              "UPDATE bookings SET gate_cleared=1,identity_verified=1 WHERE reference=?",
              b.reference());
          notifyUser(
              b.userId(), "Security cleared for " + b.reference() + ". Proceed to your gate.");
          return null;
        });
  }
}
