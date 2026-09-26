package com.aircampus;

import static org.junit.jupiter.api.Assertions.*;

import com.aircampus.api.*;
import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.service.*;
import com.aircampus.util.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AirportWorkflowTest {
  @TempDir Path temp;
  AppContext c;
  MutableClock clock;
  Session passenger, ops, checkin, ground, security, atc, maintenance, crew, manager;
  static final PaymentDetails APPROVED =
      new PaymentDetails("4111111111111111", "12/39", "123", true);

  static final class MutableClock extends Clock {
    Instant instant = Instant.parse("2026-09-06T01:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return instant;
    }
  }

  @BeforeEach
  void setup() {
    clock = new MutableClock();
    c = new AppContext(temp.resolve("test.db"), clock, true);
    passenger = login(Role.PASSENGER);
    ops = login(Role.OPS);
    checkin = login(Role.CHECK_IN);
    ground = login(Role.GROUND);
    security = login(Role.SECURITY);
    atc = login(Role.ATC);
    maintenance = login(Role.MAINTENANCE);
    crew = login(Role.AIR_CREW);
    manager = login(Role.MANAGER);
  }

  @AfterEach
  void close() {
    c.close();
  }

  Session login(Role r) {
    return c.auth.login(r.demoEmail(), "Airport123", r);
  }

  Flight flight() {
    return c.flights.all(passenger).get(0);
  }

  BookingRequest request(long id, String name, String passport, String seat) {
    return new BookingRequest(
        id,
        name,
        name,
        passport,
        LocalDate.of(2000, 1, 1),
        "",
        "",
        "",
        seat,
        FareClass.ECONOMY,
        "None",
        "Standard");
  }

  Booking book() {
    return c.bookings.book(
        passenger, request(flight().id(), "Demo Passenger", "AB123456", "3A"), APPROVED);
  }

  void allGround(long id) {
    for (String name :
        List.of("Baggage unloading", "Cleaning", "Catering", "Fueling", "Baggage loading")) {
      GroundTask t =
          c.ground.tasks(ground, id).stream()
              .filter(x -> x.name().equals(name))
              .findFirst()
              .orElseThrow();
      c.ground.advance(ground, t.id());
      c.ground.advance(ground, t.id());
    }
  }

  void ready(long id, Booking b) {
    c.bookings.webCheckIn(passenger, b.reference());
    c.security.screen(security, b.reference(), false, "PASS", "None");
    c.security.screen(security, b.reference(), true, "PASS", "None");
    c.security.gateClearance(security, b.reference(), b.passengerName(), b.passport());
    c.operations.inspect(
        maintenance, c.flights.get(ops, id).aircraftId(), true, "Inspection checklist passed");
    c.flights.signPreflight(crew, id, true);
    c.flights.updateStatus(ops, id, FlightStatus.BOARDING, 0, "");
    allGround(id);
    c.flights.checkWeight(crew, id, 200, 3000);
    c.ground.pushback(ground, id);
  }

  @Test
  void completeJourneyFromBookingToLanding() {
    Booking b = book();
    long id = b.flightId();
    ready(id, b);
    c.atc.assignRunway(atc, id, "RWY 01", clock.instant().getEpochSecond());
    c.atc.grant(atc, id);
    c.flights.updateStatus(ops, id, FlightStatus.DEPARTED, 0, "");
    c.flights.updateStatus(crew, id, FlightStatus.IN_AIR, 0, "");
    c.atc.enqueue(atc, id, "LANDING");
    c.atc.assignRunway(atc, id, "RWY 19", clock.instant().getEpochSecond());
    c.atc.grant(atc, id);
    c.flights.updateStatus(crew, id, FlightStatus.LANDED, 0, "");
    assertEquals(FlightStatus.LANDED, c.flights.get(passenger, id).status());
    assertTrue(c.atc.queue(atc).isEmpty());
  }

  @Test
  void declinedPaymentLeavesNoBooking() {
    assertThrows(
        ValidationException.class,
        () ->
            c.bookings.book(
                passenger,
                request(flight().id(), "Demo Passenger", "AB123456", "3A"),
                new PaymentDetails("4111111111111111", "12/39", "123", false)));
    assertTrue(c.bookings.mine(passenger).isEmpty());
    assertTrue(c.bookings.seats(passenger, flight().id(), FareClass.ECONOMY, "").contains("3A"));
  }

  @Test
  void seatsCannotBeDoubleBooked() {
    book();
    assertThrows(
        SeatUnavailableException.class,
        () ->
            c.bookings.book(
                passenger, request(flight().id(), "Second Passenger", "CD123456", "3A"), APPROVED));
    assertEquals(1, c.bookings.mine(passenger).size());
  }

  @Test
  void samePassportCannotBookTwoSeatsOnSameFlight() {
    book();
    assertThrows(
        ConflictException.class,
        () ->
            c.bookings.book(
                passenger, request(flight().id(), "Demo Passenger", "AB123456", "3B"), APPROVED));
    assertEquals(1, c.bookings.mine(passenger).size());
  }

  @Test
  void invalidNameAndWrongFareSeatAreRejected() {
    assertThrows(
        ValidationException.class,
        () ->
            c.bookings.book(
                passenger,
                new BookingRequest(
                    flight().id(),
                    "Demo Passenger",
                    "Different Name",
                    "AB123456",
                    LocalDate.of(2000, 1, 1),
                    "",
                    "",
                    "",
                    "3A",
                    FareClass.ECONOMY,
                    "None",
                    "Standard"),
                APPROVED));
    assertThrows(
        ValidationException.class,
        () ->
            c.bookings.book(
                passenger, request(flight().id(), "Demo Passenger", "AB123456", "1A"), APPROVED));
  }

  @Test
  void registrationUniquenessAndRoleProtection() {
    c.auth.register(
        "Second Passenger", "second@test.com", "second_user", "+85511112222", "Strong123");
    assertThrows(
        ConflictException.class,
        () ->
            c.auth.register(
                "Another Person", "SECOND@test.com", "third_user", "+85511112222", "Strong123"));
    assertThrows(
        ValidationException.class,
        () -> c.auth.login(Role.PASSENGER.demoEmail(), "Airport123", Role.OPS));
    assertThrows(
        SecurityCheckException.class, () -> c.flights.assignGate(passenger, flight().id(), "G6"));
  }

  @Test
  void passwordIsSaltedAndHashed() {
    String a = PasswordHasher.hash("Airport123"), b = PasswordHasher.hash("Airport123");
    assertNotEquals(a, b);
    assertTrue(PasswordHasher.verify("Airport123", a));
    assertFalse(PasswordHasher.verify("Wrong123", a));
  }

  @Test
  void passengerCannotReadAnotherBooking() {
    Booking b = book();
    c.auth.register("Another Person", "other@test.com", "other", "+85512345679", "Strong123");
    Session other = c.auth.login("other", "Strong123", Role.PASSENGER);
    assertThrows(SecurityCheckException.class, () -> c.bookings.get(other, b.reference()));
  }

  @Test
  void forgedSessionRejected() {
    Session forged = new Session(passenger.token(), passenger.user());
    assertThrows(SecurityCheckException.class, () -> c.bookings.mine(forged));
  }

  @Test
  void minorRequiresGuardianAndGuardianCannotCancel() {
    Booking adult = book();
    BookingRequest child =
        new BookingRequest(
            flight().id(),
            "Child Passenger",
            "Child Passenger",
            "CH123456",
            LocalDate.of(2015, 1, 1),
            adult.reference(),
            adult.passengerName(),
            "+85512345678",
            "3B",
            FareClass.ECONOMY,
            "None",
            "Standard");
    Booking result = c.bookings.book(passenger, child, APPROVED);
    assertEquals(adult.reference(), result.guardianReference());
    assertThrows(ValidationException.class, () -> c.bookings.cancel(passenger, adult.reference()));
  }

  @Test
  void unaccompaniedMinorNeedsGuardianContact() {
    BookingRequest child =
        new BookingRequest(
            flight().id(),
            "Child Passenger",
            "Child Passenger",
            "CH123456",
            LocalDate.of(2015, 1, 1),
            "",
            "",
            "",
            "3B",
            FareClass.ECONOMY,
            "Unaccompanied minor",
            "Standard");
    assertThrows(ValidationException.class, () -> c.bookings.book(passenger, child, APPROVED));
    BookingRequest accepted =
        new BookingRequest(
            child.flightId(),
            child.name(),
            child.idName(),
            child.passport(),
            child.dateOfBirth(),
            "",
            "Parent Person",
            "+85512345678",
            child.seat(),
            child.fareClass(),
            child.assistance(),
            child.meal());
    assertTrue(c.bookings.book(passenger, accepted, APPROVED).active());
  }

  @Test
  void checkInOpensAt24HoursAndClosesAt60Minutes() {
    Booking first = book();
    Flight later = c.flights.all(passenger).get(4);
    Booking b =
        c.bookings.book(
            passenger, request(later.id(), "Demo Passenger", "AB123456", "3A"), APPROVED);
    assertThrows(ValidationException.class, () -> c.bookings.webCheckIn(passenger, b.reference()));
    clock.instant = Instant.ofEpochSecond(later.estimatedDeparture() - 86400);
    c.bookings.webCheckIn(passenger, b.reference());
    assertThrows(ValidationException.class, () -> c.bookings.webCheckIn(passenger, b.reference()));
    clock.instant = Instant.ofEpochSecond(flight().estimatedDeparture() - 3599);
    assertThrows(
        ValidationException.class, () -> c.bookings.webCheckIn(passenger, first.reference()));
  }

  @Test
  void twoCountersCannotClaimSameCounterAndLogoutReleasesIt() {
    long id = flight().id();
    c.checkin.open(checkin, "C01", id);
    Session other = login(Role.CHECK_IN);
    assertThrows(ValidationException.class, () -> c.checkin.open(other, "C01", id));
    c.auth.logout(checkin);
    c.checkin.open(other, "C01", id);
    assertEquals("C01", c.checkin.currentCounter(other));
  }

  @Test
  void expiredCounterLeaseCanBeRecovered() {
    c.checkin.open(checkin, "C01", flight().id());
    clock.instant = clock.instant.plusSeconds(121);
    Session other = login(Role.CHECK_IN);
    c.checkin.open(other, "C01", flight().id());
    assertEquals("C01", c.checkin.currentCounter(other));
  }

  @Test
  void counterNeedsVerifiedIdentityThenProducesBaggageTags() {
    Booking b = book();
    c.checkin.open(checkin, "C01", b.flightId());
    assertThrows(
        ValidationException.class,
        () -> c.checkin.process(checkin, b.reference(), "3A", 1, 20, false, true));
    c.checkin.verifyIdentity(checkin, b.reference(), b.passport(), b.passengerName());
    String tags = c.checkin.process(checkin, b.reference(), "3B", 1, 20, false, true);
    assertTrue(tags.contains(b.reference() + "-B1"));
    assertEquals("3B", c.bookings.get(passenger, b.reference()).seat());
  }

  @Test
  void excessBaggageChargesOnlyDifferenceAndInvalidatesWeight() {
    Booking b = book();
    c.flights.checkWeight(ops, b.flightId(), 0, 3000);
    c.bookings.addBaggage(passenger, b.reference(), 2, 30, APPROVED);
    assertEquals(9500, c.bookings.get(passenger, b.reference()).baggageFeeCents());
    assertFalse(c.flights.get(ops, b.flightId()).weightChecked());
  }

  @Test
  void invalidBaggageAndUnacceptedFeesFail() {
    assertThrows(
        ValidationException.class, () -> BookingService.baggageFee(FareClass.ECONOMY, 0, 10));
    assertThrows(
        ValidationException.class, () -> BookingService.baggageFee(FareClass.ECONOMY, 1, 33));
    Booking b = book();
    c.checkin.open(checkin, "C01", b.flightId());
    c.checkin.verifyIdentity(checkin, b.reference(), b.passport(), b.passengerName());
    assertThrows(
        ValidationException.class,
        () -> c.checkin.process(checkin, b.reference(), "3A", 2, 30, false, true));
  }

  @Test
  void watchlistAndFailedScreeningBlockGate() {
    Booking b = book();
    c.bookings.webCheckIn(passenger, b.reference());
    c.security.screen(security, b.reference(), false, "FAIL", "Prohibited item");
    c.security.screen(security, b.reference(), true, "PASS", "None");
    assertThrows(
        ValidationException.class,
        () -> c.security.gateClearance(security, b.reference(), b.passengerName(), b.passport()));
    c.security.screen(security, b.reference(), false, "PASS", "None");
    c.security.addWatchlist(manager, b.passengerName(), "Training check");
    assertThrows(
        SecurityCheckException.class,
        () -> c.security.gateClearance(security, b.reference(), b.passengerName(), b.passport()));
  }

  @Test
  void groundTasksEnforceOrderAndPushbackPrerequisites() {
    long id = flight().id();
    GroundTask cleaning =
        c.ground.tasks(ground, id).stream()
            .filter(t -> t.name().equals("Cleaning"))
            .findFirst()
            .orElseThrow();
    assertThrows(ValidationException.class, () -> c.ground.advance(ground, cleaning.id()));
    assertThrows(ValidationException.class, () -> c.ground.pushback(ground, id));
  }

  @Test
  void boardingRequiresMaintenanceAndPreflight() {
    assertThrows(
        ValidationException.class,
        () -> c.flights.updateStatus(ops, flight().id(), FlightStatus.BOARDING, 0, ""));
    assertThrows(
        ValidationException.class, () -> c.flights.signPreflight(crew, flight().id(), true));
  }

  @Test
  void overweightAircraftIsBlocked() {
    assertThrows(
        ValidationException.class, () -> c.flights.checkWeight(crew, flight().id(), 30000, 30000));
    assertFalse(c.flights.get(crew, flight().id()).weightChecked());
  }

  @Test
  void aircraftAndGateConflictsAreRejected() {
    Flight f = flight();
    assertThrows(
        ConflictException.class,
        () ->
            c.flights.create(
                ops,
                new FlightDraft(
                    "AC999",
                    f.origin(),
                    Airport.SGN,
                    f.departure() + 60,
                    f.arrival() + 60,
                    10000,
                    f.aircraftId(),
                    "",
                    "")));
    c.flights.create(
        ops,
        new FlightDraft(
            "AC999",
            f.origin(),
            Airport.SGN,
            f.departure() + 60,
            f.arrival() + 60,
            10000,
            8,
            "",
            ""));
    Flight other =
        c.flights.all(ops).stream()
            .filter(x -> x.number().equals("AC999"))
            .findFirst()
            .orElseThrow();
    assertThrows(ConflictException.class, () -> c.flights.assignGate(ops, other.id(), f.gate()));
  }

  @Test
  void expiredCrewCannotBeAssigned() {
    long id = flight().id();
    c.flights.removeCrew(ops, id, 1);
    c.db.update("UPDATE crew SET certified_until=? WHERE id=1", c.flights.now() - 60);
    assertThrows(ValidationException.class, () -> c.flights.assignCrew(ops, id, 1));
  }

  @Test
  void crewNeedsEightHoursRest() {
    Flight f = flight();
    c.flights.create(
        ops,
        new FlightDraft(
            "AC999",
            f.origin(),
            Airport.SGN,
            f.arrival() + 3600,
            f.arrival() + 7200,
            10000,
            8,
            "",
            ""));
    long other =
        c.flights.all(ops).stream()
            .filter(x -> x.number().equals("AC999"))
            .findFirst()
            .orElseThrow()
            .id();
    assertThrows(ValidationException.class, () -> c.flights.assignCrew(ops, other, 1));
  }

  @Test
  void runwayDirectionsConflictAndWeatherBlocksClearance() {
    Booking b = book();
    ready(b.flightId(), b);
    c.atc.assignRunway(atc, b.flightId(), "RWY 01", c.flights.now());
    long other = c.flights.all(ops).get(1).id();
    c.atc.enqueue(atc, other, "TAKEOFF");
    assertThrows(
        ValidationException.class, () -> c.atc.assignRunway(atc, other, "RWY 19", c.flights.now()));
    c.atc.weather(atc, "STORM");
    assertThrows(ValidationException.class, () -> c.atc.grant(atc, b.flightId()));
  }

  @Test
  void emergencyMovesToHeadOfQueueWithoutOverridingWeather() {
    long first = flight().id(), other = c.flights.all(ops).get(1).id();
    c.atc.enqueue(atc, first, "TAKEOFF");
    c.atc.enqueue(atc, other, "TAKEOFF");
    c.atc.emergency(atc, other, "Engine warning");
    assertEquals(other, c.atc.queue(atc).get(0).flightId());
  }

  @Test
  void revokedAndExpiredClearanceCannotDepart() {
    Booking b = book();
    ready(b.flightId(), b);
    c.atc.assignRunway(atc, b.flightId(), "RWY 01", c.flights.now());
    c.atc.grant(atc, b.flightId());
    c.atc.weather(atc, "STORM");
    assertThrows(
        ValidationException.class,
        () -> c.flights.updateStatus(ops, b.flightId(), FlightStatus.DEPARTED, 0, ""));
    c.atc.weather(atc, "CLEAR");
    c.atc.grant(atc, b.flightId());
    clock.instant = clock.instant.plusSeconds(601);
    assertThrows(
        ValidationException.class,
        () -> c.flights.updateStatus(ops, b.flightId(), FlightStatus.DEPARTED, 0, ""));
  }

  @Test
  void cancellationRefundsAndInvalidatesBoardingPass() {
    Booking b = book();
    c.bookings.webCheckIn(passenger, b.reference());
    c.flights.updateStatus(ops, b.flightId(), FlightStatus.CANCELLED, 0, "Demo cancellation");
    assertThrows(
        InvalidBookingException.class, () -> c.bookings.boardingPass(passenger, b.reference()));
    assertEquals(b.paidCents(), c.bookings.get(passenger, b.reference()).refundCents());
    assertFalse(c.bookings.notices(passenger).isEmpty());
  }

  @Test
  void passengerCancellationInside24HoursReturnsHalfFare() {
    Booking b = book();
    assertEquals(b.paidCents() / 2, c.bookings.cancel(passenger, b.reference()));
    assertTrue(c.bookings.seats(passenger, b.flightId(), b.fareClass(), "").contains(b.seat()));
  }

  @Test
  void rescheduleMovesSeatAtomically() {
    Booking b = book();
    Flight next = c.flights.all(ops).get(4);
    c.bookings.reschedule(passenger, b.reference(), next.id(), "3B", APPROVED);
    Booking moved = c.bookings.get(passenger, b.reference());
    assertEquals(next.id(), moved.flightId());
    assertEquals("3B", moved.seat());
    assertTrue(c.bookings.seats(passenger, b.flightId(), FareClass.ECONOMY, "").contains("3A"));
  }

  @Test
  void incidentEscalationAndBaggageClaimTransitions() {
    Booking b = book();
    c.bookings.addBaggage(passenger, b.reference(), 1, 20, null);
    c.bookings.reportLostBag(passenger, b.reference(), "Blue suitcase, red strap");
    Claim claim = c.operations.claims(manager).get(0);
    assertThrows(
        ValidationException.class, () -> c.operations.updateClaim(manager, claim.id(), "RETURNED"));
    c.operations.updateClaim(manager, claim.id(), "SEARCHING");
    c.operations.reportIncident(
        security,
        b.flightId(),
        "SAFETY",
        "Gate G1",
        c.flights.now(),
        "Training security incident",
        "HIGH");
    assertEquals("ESCALATED", c.operations.incidents(manager).get(0).status());
  }

  @Test
  void persistenceSurvivesReopen() {
    Booking b = book();
    c.close();
    c = new AppContext(temp.resolve("test.db"), clock, true);
    passenger = login(Role.PASSENGER);
    assertEquals(b.reference(), c.bookings.mine(passenger).get(0).reference());
    assertEquals(28, c.flights.all(passenger).size());
  }

  @Test
  void observerIsNotifiedAndCanUnsubscribe() throws Exception {
    List<String> messages = new ArrayList<>();
    AutoCloseable subscription = c.events.subscribe(e -> messages.add(e.topic()));
    book();
    assertTrue(messages.contains("BOOKING_CONFIRMED"));
    int size = messages.size();
    subscription.close();
    c.atc.weather(atc, "STORM");
    assertEquals(size, messages.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0000000000000000", "4111111111111112", "abc", "123"})
  void invalidCardNumbersAreRejected(String card) {
    assertThrows(ValidationException.class, () -> Checks.luhn(card));
  }

  @Test
  void openskyParserHandlesNullPositionsAndGroundAircraft() {
    String json =
        """
{"time":100,"states":[["abc123"," LIVE123 ","Country",95,100,104.5,11.5,10000,false,230,90],["abc124",null,"Country",95,100,null,11.5,10000,false,230,90],["abc125",null,"Country",95,100,104,11.5,0,true,0,0]]}
""";
    var positions = OpenSkyProvider.parse(json);
    assertEquals(1, positions.size());
    assertEquals("LIVE123", positions.get(0).callsign());
    assertEquals(104.5, positions.get(0).longitude());
    assertFalse(positions.get(0).simulated());
  }

  @Test
  void failedLiveApiUsesLabelledFallbackAndCachesRetries() throws Exception {
    int[] calls = {0};
    try (TrackingService service =
        new TrackingService(
            () -> {
              calls[0]++;
              throw new java.io.IOException("Offline");
            },
            () -> List.of(new Position("demo", "DEMO101", 11, 104, 0, 0, 0, 100, true)))) {
      service.setLiveMode(true);
      var result = service.refresh().get();
      assertFalse(result.live());
      assertTrue(result.label().contains("DEMO fallback"));
      service.refresh().get();
      assertEquals(1, calls[0]);
    }
  }
}
