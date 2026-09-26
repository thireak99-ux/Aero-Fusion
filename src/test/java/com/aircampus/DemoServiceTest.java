package com.aircampus;

import static org.junit.jupiter.api.Assertions.*;

import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.service.*;
import com.aircampus.service.DemoService.TimePoint;
import com.aircampus.util.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for old saved dates and repeatable, time-controlled demonstrations. */
class DemoServiceTest {
  @TempDir Path temp;
  AppContext c;
  final MutableClock real = new MutableClock();

  static final class MutableClock extends Clock {
    Instant time = Instant.parse("2026-09-06T01:00:00Z");

    public Instant instant() {
      return time;
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return Clock.fixed(time, zone);
    }
  }

  @BeforeEach
  void start() {
    c = new AppContext(temp.resolve("saved.db"), real, true);
  }

  @AfterEach
  void close() {
    if (c != null) c.close();
  }

  Session login(Role role) {
    return c.auth.login(role.demoEmail(), "Airport123", role);
  }

  Flight first(Session s) {
    return c.flights.all(s).get(0);
  }

  Booking book(Session passenger, Flight f, String seat) {
    return c.bookings.book(
        passenger,
        new BookingRequest(
            f.id(),
            "Demo Passenger",
            "Demo Passenger",
            "AB123456",
            LocalDate.of(2000, 1, 1),
            "",
            "",
            "",
            seat,
            FareClass.ECONOMY,
            "None",
            "Standard"),
        PaymentDetails.demo(c.clock));
  }

  void restart() {
    c.close();
    c = null;
    c = new AppContext(temp.resolve("saved.db"), real, true);
  }

  @Test
  void expiredLegacyDatabasePreservesBookingsAndAddsCurrentWeek() {
    Session passenger = login(Role.PASSENGER);
    Flight old = first(passenger);
    c.db.update("UPDATE flights SET number='AC101' WHERE id=?", old.id());
    Booking saved = book(passenger, old, "3A");
    String passwordHash =
        c.db
            .query(
                "SELECT password_hash FROM users WHERE id=?",
                r -> r.getString(1),
                passenger.user().id())
            .get(0);
    // These tables/settings did not exist in the delivered earlier version.
    c.db.update("DROP TABLE demo_flights");
    c.db.update("DROP TABLE demo_batches");
    c.db.update("DELETE FROM settings WHERE key='demo_next_number'");
    real.time = Instant.parse("2026-09-17T01:00:00Z");
    restart();
    passenger = login(Role.PASSENGER);
    assertEquals(saved, c.bookings.get(passenger, saved.reference()));
    assertEquals(old.departure(), c.flights.get(passenger, old.id()).departure());
    assertEquals("AC101", c.flights.get(passenger, old.id()).number());
    assertEquals(
        passwordHash,
        c.db
            .query(
                "SELECT password_hash FROM users WHERE id=?",
                r -> r.getString(1),
                passenger.user().id())
            .get(0));
    assertEquals(56, c.flights.all(passenger).size());
    Flight fresh =
        c.flights.all(passenger).stream()
            .filter(f -> f.departure() > c.flights.now())
            .findFirst()
            .orElseThrow();
    assertEquals(4 * 3600, fresh.departure() - c.flights.now());
    c.bookings.webCheckIn(passenger, book(passenger, fresh, "3A").reference());
    restart();
    assertEquals(56, c.flights.all(login(Role.PASSENGER)).size());
  }

  @Test
  void runningAppRefillsExpiredSchedulesOnceWithoutRestart() {
    real.time = real.time.plus(Duration.ofDays(11));
    assertEquals(28, c.demo.ensureAvailable());
    assertEquals(0, c.demo.ensureAvailable());
    real.time = real.time.plusSeconds(61);
    assertEquals(0, c.demo.ensureAvailable());
    assertEquals(56, c.flights.all(login(Role.PASSENGER)).size());
  }

  @Test
  void freshWeekProvidesSevenSearchableDatesAndIsolatedResources() {
    Session manager = login(Role.MANAGER);
    Set<Long> originalAircraft = new HashSet<>();
    c.flights.all(manager).forEach(f -> originalAircraft.add(f.aircraftId()));
    List<Flight> week = c.demo.newWeek(manager);
    assertEquals(28, week.size());
    assertTrue(week.stream().noneMatch(f -> originalAircraft.contains(f.aircraftId())));
    assertEquals(56, c.flights.all(manager).stream().map(Flight::number).distinct().count());
    for (int day = 0; day < 7; day++) {
      LocalDate date = Formats.date(week.get(day * 4).departure());
      assertFalse(c.flights.search(manager, Airport.KTI, Airport.BKK, date).isEmpty());
    }
    assertEquals(
        0,
        c.db.count(
            "SELECT COUNT(*) FROM flights a JOIN flights b ON a.id<b.id "
                + "AND a.origin=b.origin AND a.gate=b.gate WHERE a.gate<>'' "
                + "AND abs(a.departure-b.departure)<5400"));
    assertEquals(
        0,
        c.db.count(
            "SELECT COUNT(*) FROM flight_crew a JOIN flight_crew b ON"
                + " a.crew_id=b.crew_id AND a.flight_id<b.flight_id JOIN flights x ON"
                + " x.id=a.flight_id JOIN flights y ON y.id=b.flight_id WHERE"
                + " x.departure<y.arrival+28800 AND y.departure<x.arrival+28800"));
  }

  @Test
  void futureYearGetsFreshCertifiedCrewAndWorkingDemoPayment() {
    Session manager = login(Role.MANAGER), passenger = login(Role.PASSENGER);
    c.demo.setTime(manager, Instant.parse("2050-01-02T03:00:00Z"));
    Flight f = c.demo.newWeek(manager).get(0);
    assertTrue(
        c.flights.assignedCrew(manager, f.id()).stream()
            .allMatch(person -> person.certifiedUntil() > f.arrival()));
    assertEquals("12/53", PaymentDetails.demo(c.clock).expiry());
    assertTrue(book(passenger, f, "3A").active());
    Session maintenance = login(Role.MAINTENANCE), crew = login(Role.AIR_CREW);
    c.operations.inspect(maintenance, f.aircraftId(), true, "Current demo inspection");
    c.flights.signPreflight(crew, f.id(), true);
    c.flights.updateStatus(login(Role.OPS), f.id(), FlightStatus.BOARDING, 0, "");
  }

  @Test
  void pausedWorkflowSurvivesComputerTimePassingAndCompletesLanding() {
    Session manager = login(Role.MANAGER), passenger = login(Role.PASSENGER);
    Flight f = first(passenger);
    c.demo.useFlightPoint(manager, f.id(), TimePoint.WORKFLOW);
    long paused = c.flights.now();
    real.time = real.time.plus(Duration.ofDays(2));
    assertEquals(paused, c.flights.now());
    assertEquals(c.clock.instant(), c.clock.withZone(Formats.ZONE).instant());
    assertEquals(0, c.demo.ensureAvailable());
    Booking b = book(passenger, f, "3A");
    prepare(passenger, f, b);
    Session atc = login(Role.ATC), ops = login(Role.OPS), crew = login(Role.AIR_CREW);
    c.atc.assignRunway(atc, f.id(), "RWY 01", c.flights.now());
    c.atc.grant(atc, f.id());
    c.flights.updateStatus(ops, f.id(), FlightStatus.DEPARTED, 0, "");
    c.flights.updateStatus(crew, f.id(), FlightStatus.IN_AIR, 0, "");
    c.demo.useFlightPoint(manager, f.id(), TimePoint.LANDING);
    c.atc.enqueue(atc, f.id(), "LANDING");
    c.atc.assignRunway(atc, f.id(), "RWY 19", c.flights.now());
    c.atc.grant(atc, f.id());
    c.flights.updateStatus(crew, f.id(), FlightStatus.LANDED, 0, "");
    assertEquals(FlightStatus.LANDED, c.flights.get(passenger, f.id()).status());
  }

  void prepare(Session passenger, Flight f, Booking b) {
    c.bookings.webCheckIn(passenger, b.reference());
    Session security = login(Role.SECURITY), maintenance = login(Role.MAINTENANCE);
    Session crew = login(Role.AIR_CREW), ops = login(Role.OPS), ground = login(Role.GROUND);
    c.security.screen(security, b.reference(), false, "PASS", "None");
    c.security.screen(security, b.reference(), true, "PASS", "None");
    c.security.gateClearance(security, b.reference(), b.passengerName(), b.passport());
    c.operations.inspect(maintenance, f.aircraftId(), true, "All inspection checks passed");
    c.flights.signPreflight(crew, f.id(), true);
    c.flights.updateStatus(ops, f.id(), FlightStatus.BOARDING, 0, "");
    for (GroundTask task : c.ground.tasks(ground, f.id())) {
      c.ground.advance(ground, task.id());
      c.ground.advance(ground, task.id());
    }
    c.flights.checkWeight(crew, f.id(), 200, 3000);
    c.ground.pushback(ground, f.id());
  }

  @Test
  void checkInPresetsExerciseBothInclusiveBoundariesAndClosedWindow() {
    Session manager = login(Role.MANAGER), passenger = login(Role.PASSENGER);
    Flight f = first(passenger);
    Booking b = book(passenger, f, "3A");
    c.demo.useFlightPoint(manager, f.id(), TimePoint.BOOKING);
    assertThrows(ValidationException.class, () -> c.bookings.webCheckIn(passenger, b.reference()));
    c.demo.useFlightPoint(manager, f.id(), TimePoint.CHECK_IN_OPENS);
    c.bookings.webCheckIn(passenger, b.reference());
    Session checkin = login(Role.CHECK_IN);
    c.checkin.open(checkin, "C01", f.id());
    c.demo.useFlightPoint(manager, f.id(), TimePoint.CHECK_IN_CUTOFF);
    c.checkin.open(checkin, "C01", f.id());
    assertEquals("C01", c.checkin.currentCounter(checkin));
    c.demo.useFlightPoint(manager, f.id(), TimePoint.CHECK_IN_CLOSED);
    assertEquals("Closed", c.checkin.currentCounter(checkin));
    assertThrows(ValidationException.class, () -> c.checkin.open(checkin, "C01", f.id()));
    assertThrows(ValidationException.class, () -> book(passenger, f, "3B"));
  }

  @Test
  void refundPresetsUseAppClockAndKeepCutoffRules() {
    Session manager = login(Role.MANAGER), passenger = login(Role.PASSENGER);
    Flight f = first(passenger);
    c.demo.useFlightPoint(manager, f.id(), TimePoint.BOOKING);
    Booking b = book(passenger, f, "3A");
    assertEquals(b.paidCents(), c.bookings.refundQuote(passenger, b.reference()));
    c.demo.useFlightPoint(manager, f.id(), TimePoint.WORKFLOW);
    assertEquals(b.paidCents() / 2, c.bookings.refundQuote(passenger, b.reference()));
    c.demo.useFlightPoint(manager, f.id(), TimePoint.CHECK_IN_CLOSED);
    assertThrows(ValidationException.class, () -> c.bookings.cancel(passenger, b.reference()));
    assertTrue(c.bookings.get(passenger, b.reference()).active());
  }

  @Test
  void timeJumpRevokesClearanceBeforeAnotherDepartureAttempt() {
    Session passenger = login(Role.PASSENGER), manager = login(Role.MANAGER);
    Flight f = first(passenger);
    prepare(passenger, f, book(passenger, f, "3A"));
    Session atc = login(Role.ATC), ops = login(Role.OPS), checkin = login(Role.CHECK_IN);
    c.checkin.open(checkin, "C01", f.id());
    c.atc.assignRunway(atc, f.id(), "RWY 01", c.flights.now());
    c.atc.grant(atc, f.id());
    c.demo.freezeNow(manager);
    assertEquals("Closed", c.checkin.currentCounter(checkin));
    assertEquals("ASSIGNED", c.atc.queue(atc).get(0).state());
    assertThrows(
        ValidationException.class,
        () -> c.flights.updateStatus(ops, f.id(), FlightStatus.DEPARTED, 0, ""));
    c.atc.grant(atc, f.id());
    c.flights.updateStatus(ops, f.id(), FlightStatus.DEPARTED, 0, "");
  }

  @Test
  void expiredLeaseCanBeRecoveredWhileDemoTimeIsPaused() {
    Session manager = login(Role.MANAGER), checkin = login(Role.CHECK_IN);
    Flight f = first(manager);
    c.demo.useFlightPoint(manager, f.id(), TimePoint.WORKFLOW);
    c.checkin.open(checkin, "C01", f.id());
    real.time = real.time.plusSeconds(121);
    Session other = login(Role.CHECK_IN);
    c.checkin.open(other, "C01", f.id());
    assertEquals("C01", c.checkin.currentCounter(other));
    assertEquals("Closed", c.checkin.currentCounter(checkin));
  }

  @Test
  void computerTimeAndRestartNeverRetainAnOldPausedDate() {
    Session manager = login(Role.MANAGER);
    c.demo.setTime(manager, Instant.parse("2030-01-02T03:00:00Z"));
    assertEquals(0, c.demo.ensureAvailable());
    c.demo.useRealTime(manager);
    assertFalse(c.clock.isFrozen());
    assertEquals(real.instant(), c.clock.instant());
    c.demo.useFlightPoint(manager, first(manager).id(), TimePoint.WORKFLOW);
    restart();
    assertFalse(c.clock.isFrozen());
    assertEquals(real.instant(), c.clock.instant());
  }

  @Test
  void onlyManagerCanAddSchedulesOrChangeTime() {
    Session passenger = login(Role.PASSENGER);
    assertThrows(SecurityCheckException.class, () -> c.demo.newWeek(passenger));
    assertThrows(SecurityCheckException.class, () -> c.demo.freezeNow(passenger));
    assertThrows(SecurityCheckException.class, () -> c.demo.useRealTime(passenger));
    assertThrows(
        SecurityCheckException.class,
        () -> c.demo.useFlightPoint(passenger, first(passenger).id(), TimePoint.WORKFLOW));
    assertFalse(c.clock.isFrozen());
    assertEquals(28, c.flights.all(passenger).size());
  }
}
