package com.aircampus;

import com.aircampus.api.*;
import com.aircampus.event.EventBus;
import com.aircampus.repository.*;
import com.aircampus.service.*;
import com.aircampus.util.DemoClock;
import java.nio.file.*;
import java.time.Clock;

public final class AppContext implements AutoCloseable {
  public final Database db;
  public final EventBus events = new EventBus();
  public final AuthService auth;
  public final FlightService flights;
  public final BookingService bookings;
  public final CheckInService checkin;
  public final GroundService ground;
  public final SecurityService security;
  public final AtcService atc;
  public final OperationsService operations;
  public final TrackingService tracking;
  public final DemoClock clock;
  public final DemoService demo;

  public AppContext(Path database, Clock realClock, boolean seed) {
    this.clock = new DemoClock(realClock);
    db = new Database(database);
    if (seed) DemoData.seed(db, clock);
    auth = new AuthService(db, realClock);
    flights = new FlightService(db, auth, clock, events);
    bookings = new BookingService(db, auth, clock, events);
    checkin = new CheckInService(db, auth, clock, events, bookings);
    ground = new GroundService(db, auth, clock, events);
    security = new SecurityService(db, auth, clock, events);
    operations = new OperationsService(db, auth, clock, events);
    atc = new AtcService(db, auth, clock, events, flights);
    demo = new DemoService(db, auth, clock, events, flights, seed);
    if (seed) demo.ensureAvailable();
    tracking =
        new TrackingService(
            new OpenSkyProvider(),
            new MockFlightProvider(new FlightRepository(db)::findAll, clock));
  }

  @Override
  public void close() {
    tracking.close();
    db.close();
  }
}
