package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public abstract class ServiceSupport {
  protected final Database db;
  protected final AuthService auth;
  protected final Clock clock;
  protected final EventBus events;
  protected final FlightRepository flights;
  protected final BookingRepository bookings;

  protected ServiceSupport(Database db, AuthService auth, Clock clock, EventBus events) {
    this.db = db;
    this.auth = auth;
    this.clock = clock;
    this.events = events;
    flights = new FlightRepository(db);
    bookings = new BookingRepository(db);
  }

  public long now() {
    return clock.instant().getEpochSecond();
  }

  public LocalDate today() {
    return clock.instant().atZone(Formats.ZONE).toLocalDate();
  }

  protected Flight flight(long id) {
    return flights
        .findById(id)
        .orElseThrow(() -> new ValidationException("Select an existing flight."));
  }

  protected Aircraft aircraft(long id) {
    return db.query("SELECT * FROM aircraft WHERE id=?", Mappers.AIRCRAFT, id).stream()
        .findFirst()
        .orElseThrow(() -> new ValidationException("Select an existing aircraft."));
  }

  protected Booking booking(String reference) {
    String ref = Checks.text(reference, "Booking reference", 12).toUpperCase(Locale.ROOT);
    return bookings
        .findById(ref)
        .orElseThrow(() -> new InvalidBookingException("Booking reference was not found."));
  }

  protected Booking owned(Session session, String ref) {
    auth.require(session);
    Booking b = booking(ref);
    if (session.user().role() == Role.PASSENGER && b.userId() != session.user().id())
      throw new SecurityCheckException("This booking does not belong to your account.");
    return b;
  }

  protected void active(Booking b) {
    if (!b.active() || flight(b.flightId()).status() == FlightStatus.CANCELLED)
      throw new InvalidBookingException("This booking or flight is cancelled.");
  }

  protected void checkInWindow(Flight f) {
    long remaining = f.estimatedDeparture() - now();
    Checks.that(f.status().isOpen(), "This flight is closed for check-in.");
    Checks.that(
        remaining <= 86400 && remaining >= 3600,
        "Check-in opens 24 hours before estimated departure and closes 60 minutes before.");
  }

  protected void mutableFlight(Flight f) {
    Checks.that(f.status().isOpen(), "This flight has departed, landed or been cancelled.");
  }

  protected void invalidateLoad(long flightId) {
    db.update("UPDATE flights SET weight_checked=0,pushback=0 WHERE id=?", flightId);
    db.update(
        "UPDATE runway_slots SET state='ASSIGNED' WHERE flight_id=? AND operation='TAKEOFF' AND"
            + " state='CLEARED'",
        flightId);
  }

  public void audit(Session session, String action, String details) {
    db.update(
        "INSERT INTO audit(created_at,actor,action,details) VALUES(?,?,?,?)",
        now(),
        session == null ? "Login" : session.user().username(),
        action,
        details);
  }

  protected void notifyUser(long userId, String message) {
    db.update(
        "INSERT INTO notifications(user_id,created_at,message) VALUES(?,?,?)",
        userId,
        now(),
        message);
  }

  protected void notifyFlight(long id, String message) {
    for (Long uid :
        db.query("SELECT DISTINCT user_id FROM bookings WHERE flight_id=?", r -> r.getLong(1), id))
      notifyUser(uid, message);
  }

  protected <T> T change(Session session, String action, long flightId, Supplier<T> work) {
    try {
      T value =
          db.transaction(
              () -> {
                T result = work.get();
                audit(session, action, "Flight ID: " + flightId);
                return result;
              });
      events.publish(new AirportEvent(action, flightId, action.replace('_', ' ')));
      return value;
    } catch (AppException e) {
      audit(session, "VALIDATION_FAILED", action + ": " + e.getMessage());
      throw e;
    }
  }

  public List<Notice> notices(Session session) {
    auth.require(session);
    return db.query(
        "SELECT * FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 100",
        Mappers.NOTICE,
        session.user().id());
  }

  public void readNotices(Session session) {
    auth.require(session);
    change(
        session,
        "READ_NOTICES",
        0,
        () -> db.update("UPDATE notifications SET is_read=1 WHERE user_id=?", session.user().id()));
  }
}
