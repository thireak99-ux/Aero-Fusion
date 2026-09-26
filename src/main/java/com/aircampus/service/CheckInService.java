package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.Clock;
import java.util.*;

public final class CheckInService extends ServiceSupport {
  private final BookingService bookingService;
  public static final List<String> COUNTERS = List.of("C01", "C02", "C03", "C04");

  public CheckInService(
      Database db, AuthService auth, Clock clock, EventBus events, BookingService bookingService) {
    super(db, auth, clock, events);
    this.bookingService = bookingService;
  }

  public String currentCounter(Session s) {
    auth.require(s, Role.CHECK_IN);
    return db
        .query(
            "SELECT counter FROM counters WHERE session_token=? AND expires_at>?",
            r -> r.getString(1),
            s.token(),
            auth.leaseNow())
        .stream()
        .findFirst()
        .orElse("Closed");
  }

  public long assignedFlight(Session s) {
    auth.require(s, Role.CHECK_IN);
    return db
        .query(
            "SELECT flight_id FROM counters WHERE session_token=? AND expires_at>?",
            r -> r.getLong(1),
            s.token(),
            auth.leaseNow())
        .stream()
        .findFirst()
        .orElse(0L);
  }

  public void open(Session s, String counter, long flightId) {
    auth.require(s, Role.CHECK_IN);
    change(
        s,
        "OPEN_COUNTER",
        flightId,
        () -> {
          Checks.that(COUNTERS.contains(counter), "Select a valid counter.");
          checkInWindow(flight(flightId));
          db.update("DELETE FROM counters WHERE expires_at<=?", auth.leaseNow());
          Checks.that(
              db.count(
                      "SELECT COUNT(*) FROM counters WHERE counter=? AND session_token<>?",
                      counter,
                      s.token())
                  == 0,
              "This counter is already in use by another session.");
          db.update("DELETE FROM counters WHERE session_token=?", s.token());
          db.update(
              "INSERT INTO counters VALUES(?,?,?,?,?)",
              counter,
              s.token(),
              s.user().id(),
              flightId,
              auth.leaseNow() + 120);
          return null;
        });
  }

  public void close(Session s) {
    auth.require(s, Role.CHECK_IN);
    change(
        s,
        "CLOSE_COUNTER",
        0,
        () -> db.update("DELETE FROM counters WHERE session_token=?", s.token()));
  }

  private Booking atCounter(Session s, String ref) {
    Booking b = booking(ref);
    active(b);
    Checks.that(
        b.flightId() == assignedFlight(s), "Open a counter for this passenger's flight first.");
    checkInWindow(flight(b.flightId()));
    return b;
  }

  public List<Booking> search(Session s, String query) {
    auth.require(s, Role.CHECK_IN);
    long id = assignedFlight(s);
    Checks.that(id > 0, "Open a counter first.");
    checkInWindow(flight(id));
    String q = Checks.text(query, "Booking reference / passport", 20).toUpperCase(Locale.ROOT);
    List<Booking> found =
        db.query(
            "SELECT * FROM bookings WHERE flight_id=? AND status<>'CANCELLED' AND (reference=? OR"
                + " passport=?)",
            Mappers.BOOKING,
            id,
            q,
            q);
    Checks.that(
        !found.isEmpty(),
        "No passenger matches this reference or passport on your assigned flight.");
    return found;
  }

  public List<Booking> manifest(Session s) {
    auth.require(s, Role.CHECK_IN);
    long id = assignedFlight(s);
    return id == 0 ? List.of() : bookings.manifest(id);
  }

  public void verifyIdentity(Session s, String ref, String passport, String idName) {
    auth.require(s, Role.CHECK_IN);
    change(
        s,
        "VERIFY_IDENTITY",
        0,
        () -> {
          Booking b = atCounter(s, ref);
          Checks.that(
              b.passport().equals(Checks.passport(passport))
                  && Checks.normalizedName(b.passengerName()).equals(Checks.normalizedName(idName)),
              "Name or passport does not match the booking.");
          db.update("UPDATE bookings SET identity_verified=1 WHERE reference=?", b.reference());
          return null;
        });
  }

  public String process(
      Session s,
      String ref,
      String seat,
      int pieces,
      double kg,
      boolean feeAccepted,
      boolean piecesInspected) {
    auth.require(s, Role.CHECK_IN);
    return change(
        s,
        "COUNTER_CHECK_IN",
        0,
        () -> {
          Booking b = atCounter(s, ref);
          Checks.that(b.identityVerified(), "Verify the passenger's photo ID first.");
          Checks.that(
              piecesInspected || pieces == 0, "Confirm each individual bag is at most 32 kg.");
          Checks.that(
              bookingService.seats(s, b.flightId(), b.fareClass(), b.reference()).contains(seat),
              "Selected seat is occupied or outside this fare class.");
          long fee = BookingService.baggageFee(b.fareClass(), pieces, kg);
          Checks.that(
              fee <= b.baggageFeeCents() || feeAccepted,
              "Confirm collection of the simulated excess fee before check-in.");
          if (b.assistance().equals("Unaccompanied minor")) {
            Checks.name(b.guardianName());
            Checks.phone(b.guardianPhone());
          }
          bookingService.saveBaggage(b, pieces, kg, fee);
          db.update(
              "UPDATE bookings SET"
                  + " seat=?,status='CHECKED_IN',gate_cleared=0,refund_cents=refund_cents+? WHERE"
                  + " reference=?",
              seat,
              Math.max(0, b.baggageFeeCents() - fee),
              b.reference());
          notifyUser(
              b.userId(), "Counter check-in saved: " + b.reference() + ", seat " + seat + ".");
          StringBuilder tags = new StringBuilder("SIMULATED BAGGAGE TAGS\n");
          for (int i = 1; i <= pieces; i++)
            tags.append(b.reference())
                .append("-B")
                .append(i)
                .append(" | ")
                .append(flight(b.flightId()).number())
                .append("\n");
          return tags.append("Total weight: ")
              .append(kg)
              .append(" kg\nTotal excess fee: ")
              .append(Formats.money(fee))
              .toString();
        });
  }
}
