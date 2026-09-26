package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.*;
import java.util.*;

public final class BookingService extends ServiceSupport {
  public BookingService(Database db, AuthService auth, Clock clock, EventBus events) {
    super(db, auth, clock, events);
  }

  public List<Booking> mine(Session s) {
    auth.require(s, Role.PASSENGER);
    return bookings.byUser(s.user().id());
  }

  public Booking get(Session s, String ref) {
    auth.require(
        s, Role.PASSENGER, Role.CHECK_IN, Role.SECURITY, Role.OPS, Role.AIR_CREW, Role.MANAGER);
    return owned(s, ref);
  }

  public List<String> seats(Session s, long id, FareClass fare, String excludingReference) {
    auth.require(s);
    Checks.required(fare, "fare class");
    Flight f = flight(id);
    Aircraft a = aircraft(f.aircraftId());
    Set<String> used = new HashSet<>();
    bookings.manifest(id).stream()
        .filter(b -> !b.reference().equals(excludingReference))
        .forEach(b -> used.add(b.seat()));
    List<String> free = new ArrayList<>();
    for (int row = 1; row <= a.rows(); row++)
      for (char c = 'A'; c <= 'F'; c++)
        if ((fare == FareClass.BUSINESS) == (row <= 2) && !used.contains(row + "" + c))
          free.add(row + "" + c);
    return free;
  }

  private void seat(Flight f, String seat, FareClass fare, String except) {
    Checks.required(fare, "fare class");
    Checks.that(seat != null && seat.matches("[1-9][0-9]?[A-F]"), "Choose a seat, for example 3A.");
    int row = Integer.parseInt(seat.substring(0, seat.length() - 1));
    Checks.that(row <= aircraft(f.aircraftId()).rows(), "Seat does not exist on this aircraft.");
    Checks.that(
        (fare == FareClass.BUSINESS) == (row <= 2),
        "Rows 1–2 are Business; other rows are Economy.");
    if (bookings.manifest(f.id()).stream()
        .anyMatch(b -> b.seat().equals(seat) && !b.reference().equals(except)))
      throw new SeatUnavailableException("That seat is already booked.");
  }

  private void payment(PaymentDetails p) {
    Checks.required(p, "payment");
    Checks.payment(
        p.cardNumber(),
        p.expiry(),
        p.cvv(),
        YearMonth.from(clock.instant().atZone(Formats.ZONE)),
        p.approve());
  }

  private void bookable(Flight f) {
    Checks.that(
        f.status().isOpen() && f.estimatedDeparture() > now() + 3600,
        "Booking is closed for this flight (60-minute cutoff).");
  }

  private void guardian(Session s, BookingRequest r) {
    Checks.birthDate(r.dateOfBirth(), today());
    int age = Period.between(r.dateOfBirth(), today()).getYears();
    if (age < 18) {
      Checks.name(r.guardianName());
      Checks.phone(r.guardianPhone());
      if (r.assistance().equals("Unaccompanied minor")) {
        Checks.that(age >= 5 && age <= 17, "Unaccompanied-minor service is offered for ages 5–17.");
      } else {
        Booking g = owned(s, Checks.text(r.guardianReference(), "Guardian booking reference", 12));
        active(g);
        Checks.that(
            g.flightId() == r.flightId(),
            "Guardian must have an active booking on the same flight.");
        Checks.that(
            Period.between(LocalDate.parse(g.dateOfBirth()), today()).getYears() >= 18,
            "Linked guardian must be an adult.");
        Checks.that(
            Checks.normalizedName(g.passengerName())
                .equals(Checks.normalizedName(r.guardianName())),
            "Guardian name must match the linked booking.");
      }
    } else
      Checks.that(
          !r.assistance().equals("Unaccompanied minor"),
          "Unaccompanied-minor service is only for ages 5–17.");
  }

  public Booking book(Session s, BookingRequest r, PaymentDetails p) {
    auth.require(s, Role.PASSENGER);
    return change(
        s,
        "BOOKING_CONFIRMED",
        r == null ? 0 : r.flightId(),
        () -> {
          Checks.required(r, "booking").validate();
          Flight f = flight(r.flightId());
          bookable(f);
          guardian(s, r);
          seat(f, r.seat(), r.fareClass(), "");
          payment(p);
          String ref =
              UUID.randomUUID()
                  .toString()
                  .replace("-", "")
                  .substring(0, 8)
                  .toUpperCase(Locale.ROOT);
          db.update(
              "INSERT INTO"
                  + " bookings(reference,user_id,flight_id,passenger_name,passport,date_of_birth,guardian_reference,guardian_name,guardian_phone,seat,fare_class,paid_cents,assistance,meal)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
              ref,
              s.user().id(),
              f.id(),
              Checks.name(r.name()),
              Checks.passport(r.passport()),
              r.dateOfBirth().toString(),
              r.guardianReference() == null
                  ? ""
                  : r.guardianReference().trim().toUpperCase(Locale.ROOT),
              r.guardianName() == null ? "" : r.guardianName().trim(),
              r.guardianPhone() == null ? "" : r.guardianPhone().trim(),
              r.seat(),
              r.fareClass(),
              r.fareClass().price(f.priceCents()),
              r.assistance(),
              r.meal());
          invalidateLoad(f.id());
          notifyUser(
              s.user().id(),
              "Booking " + ref + " confirmed for " + f.number() + ". Payment simulated.");
          return booking(ref);
        });
  }

  public void webCheckIn(Session s, String ref) {
    auth.require(s, Role.PASSENGER);
    change(
        s,
        "WEB_CHECK_IN",
        0,
        () -> {
          Booking b = owned(s, ref);
          active(b);
          checkInWindow(flight(b.flightId()));
          Checks.that(!b.checkedIn(), "This booking is already checked in.");
          db.update("UPDATE bookings SET status='CHECKED_IN' WHERE reference=?", b.reference());
          notifyUser(
              b.userId(), "Check-in complete for " + b.reference() + ". Show your ID at security.");
          return null;
        });
  }

  public Booking boardingPass(Session s, String ref) {
    auth.require(
        s, Role.PASSENGER, Role.CHECK_IN, Role.SECURITY, Role.OPS, Role.AIR_CREW, Role.MANAGER);
    Booking b = owned(s, ref);
    active(b);
    Checks.that(b.checkedIn(), "Check in before viewing a boarding pass.");
    return b;
  }

  public static long baggageFee(FareClass fare, int pieces, double kg) {
    Checks.required(fare, "fare class");
    Checks.that(pieces >= 0 && pieces <= 4, "Baggage may contain 0–4 pieces.");
    Checks.range(kg, "Total baggage weight", 0, 128);
    Checks.that(
        (pieces == 0 && kg == 0) || (pieces > 0 && kg > 0 && kg <= pieces * 32),
        "Provide positive weight for each piece; total must be at most 32 kg per piece on average."
            + " Staff must also inspect individual pieces.");
    return Math.round(Math.max(0, kg - fare.kg()) * 1000)
        + Math.max(0, pieces - fare.pieces()) * 2500L;
  }

  public void addBaggage(
      Session s, String ref, int pieces, double kg, PaymentDetails paymentDetails) {
    auth.require(s, Role.PASSENGER);
    change(
        s,
        "BAGGAGE_UPDATED",
        0,
        () -> {
          Booking b = owned(s, ref);
          active(b);
          bookable(flight(b.flightId()));
          Checks.that(
              !b.checkedIn(), "After check-in, baggage changes must be made at the counter.");
          long fee = baggageFee(b.fareClass(), pieces, kg);
          Checks.that(
              pieces >= b.baggagePieces() && kg >= b.baggageKg(),
              "This action adds baggage. Reductions are handled by counter staff.");
          if (fee > b.baggageFeeCents()) payment(paymentDetails);
          saveBaggage(b, pieces, kg, fee);
          notifyUser(
              b.userId(),
              "Baggage updated for " + b.reference() + ". Total excess fee: " + Formats.money(fee));
          return null;
        });
  }

  void saveBaggage(Booking b, int pieces, double kg, long fee) {
    db.update(
        "UPDATE bookings SET"
            + " baggage_pieces=?,baggage_kg=?,baggage_fee_cents=?,baggage_screen='PENDING',gate_cleared=0"
            + " WHERE reference=?",
        pieces,
        kg,
        fee,
        b.reference());
    invalidateLoad(b.flightId());
  }

  private void noDependents(Booking b) {
    Checks.that(
        db.count(
                "SELECT COUNT(*) FROM bookings WHERE guardian_reference=? AND status<>'CANCELLED'",
                b.reference())
            == 0,
        "This booking is linked to a minor. Cancel or reschedule the minor first.");
  }

  public long refundQuote(Session s, String ref) {
    auth.require(s, Role.PASSENGER);
    Booking b = owned(s, ref);
    active(b);
    return (flight(b.flightId()).estimatedDeparture() - now() >= 86400
            ? b.paidCents()
            : b.paidCents() / 2)
        + b.baggageFeeCents();
  }

  public long cancel(Session s, String ref) {
    auth.require(s, Role.PASSENGER);
    return change(
        s,
        "BOOKING_CANCELLED",
        0,
        () -> {
          Booking b = owned(s, ref);
          active(b);
          Checks.that(
              flight(b.flightId()).estimatedDeparture() > now() + 3600
                  && flight(b.flightId()).status().isOpen(),
              "Cancellation closes 60 minutes before departure.");
          noDependents(b);
          long refund = refundQuote(s, ref);
          db.update(
              "UPDATE bookings SET status='CANCELLED',refund_cents=?,gate_cleared=0 WHERE"
                  + " reference=?",
              refund,
              b.reference());
          invalidateLoad(b.flightId());
          notifyUser(
              b.userId(),
              "Booking " + b.reference() + " cancelled. Simulated refund " + Formats.money(refund));
          return refund;
        });
  }

  public long rescheduleQuote(Session s, String ref, long targetId) {
    auth.require(s, Role.PASSENGER);
    Booking b = owned(s, ref);
    Flight target = flight(targetId);
    long fareDifference = b.fareClass().price(target.priceCents()) - b.paidCents();
    return fareDifference + (flight(b.flightId()).estimatedDeparture() - now() < 86400 ? 2500 : 0);
  }

  public void reschedule(Session s, String ref, long targetId, String newSeat, PaymentDetails p) {
    auth.require(s, Role.PASSENGER);
    change(
        s,
        "BOOKING_RESCHEDULED",
        targetId,
        () -> {
          Booking b = owned(s, ref);
          active(b);
          noDependents(b);
          Flight old = flight(b.flightId()), target = flight(targetId);
          bookable(old);
          bookable(target);
          Checks.that(
              old.id() != target.id()
                  && old.origin() == target.origin()
                  && old.destination() == target.destination(),
              "Choose a different flight on the same route.");
          Checks.that(
              !b.checkedIn(),
              "Reschedule before check-in; a checked-in passenger must cancel and rebook.");
          if (!b.guardianReference().isBlank()) {
            Booking g = owned(s, b.guardianReference());
            active(g);
            Checks.that(
                g.flightId() == target.id(),
                "Linked guardian must already be on the new flight. For a family move, cancel minor"
                    + " bookings first, reschedule the adult, then rebook minors.");
          }
          seat(target, newSeat, b.fareClass(), b.reference());
          long difference = rescheduleQuote(s, ref, targetId);
          if (difference > 0) payment(p);
          db.update(
              "UPDATE bookings SET"
                  + " flight_id=?,seat=?,paid_cents=?,status='CONFIRMED',identity_verified=0,passenger_screen='PENDING',baggage_screen='PENDING',gate_cleared=0,refund_cents=refund_cents+?"
                  + " WHERE reference=?",
              target.id(),
              newSeat,
              b.fareClass().price(target.priceCents()),
              Math.max(0, -difference),
              b.reference());
          invalidateLoad(old.id());
          invalidateLoad(target.id());
          notifyUser(
              b.userId(),
              "Booking "
                  + b.reference()
                  + " moved to "
                  + target.number()
                  + ". "
                  + (difference >= 0 ? "Simulated amount paid " : "Simulated refund ")
                  + Formats.money(Math.abs(difference)));
          return null;
        });
  }

  public void requests(
      Session s,
      String ref,
      String assistance,
      String meal,
      String guardianName,
      String guardianPhone) {
    auth.require(s, Role.PASSENGER, Role.CHECK_IN);
    change(
        s,
        "SPECIAL_REQUEST",
        0,
        () -> {
          Booking b = owned(s, ref);
          active(b);
          bookable(flight(b.flightId()));
          if (s.user().role() == Role.CHECK_IN)
            Checks.that(
                db.count(
                        "SELECT COUNT(*) FROM counters WHERE session_token=? AND flight_id=? AND"
                            + " expires_at>?",
                        s.token(),
                        b.flightId(),
                        now())
                    == 1,
                "Open a counter for this passenger's flight first.");
          BookingRequest r =
              new BookingRequest(
                  b.flightId(),
                  b.passengerName(),
                  b.passengerName(),
                  b.passport(),
                  LocalDate.parse(b.dateOfBirth()),
                  b.guardianReference(),
                  guardianName,
                  guardianPhone,
                  b.seat(),
                  b.fareClass(),
                  assistance,
                  meal);
          r.validate();
          guardian(s, r);
          db.update(
              "UPDATE bookings SET assistance=?,meal=?,guardian_name=?,guardian_phone=? WHERE"
                  + " reference=?",
              assistance,
              meal,
              guardianName == null ? "" : guardianName.trim(),
              guardianPhone == null ? "" : guardianPhone.trim(),
              b.reference());
          return null;
        });
  }

  public List<Claim> claims(Session s) {
    auth.require(s, Role.PASSENGER);
    return db.query(
        "SELECT * FROM claims WHERE user_id=? ORDER BY id DESC", Mappers.CLAIM, s.user().id());
  }

  public void reportLostBag(Session s, String ref, String description) {
    auth.require(s, Role.PASSENGER);
    change(
        s,
        "LOST_BAG",
        0,
        () -> {
          Booking b = owned(s, ref);
          Checks.that(b.baggagePieces() > 0, "This booking has no recorded baggage.");
          String d = Checks.text(description, "Bag description", 500);
          db.update(
              "INSERT INTO claims(user_id,reference,description) VALUES(?,?,?)",
              s.user().id(),
              b.reference(),
              d);
          return null;
        });
  }
}
