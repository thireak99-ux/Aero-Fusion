package com.aircampus.api;

import com.aircampus.model.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

/** Positions are illustrative and always carry the simulated flag. */
public final class MockFlightProvider implements FlightDataProvider {
  private final Supplier<List<Flight>> flights;
  private final Clock clock;

  public MockFlightProvider(Supplier<List<Flight>> flights, Clock clock) {
    this.flights = flights;
    this.clock = clock;
  }

  @Override
  public List<Position> fetch() {
    long now = clock.instant().getEpochSecond();
    List<Position> result = new ArrayList<>();
    for (Flight f : flights.get())
      if (f.status() != FlightStatus.CANCELLED && f.status() != FlightStatus.LANDED) {
        double t = 0.15 + Math.floorMod(now / 5 + f.id() * 7, 120) / 170.0;
        result.add(
            new Position(
                "demo" + f.id(),
                f.callsign().isBlank() ? f.number() : f.callsign(),
                f.origin().latitude() + (f.destination().latitude() - f.origin().latitude()) * t,
                f.origin().longitude() + (f.destination().longitude() - f.origin().longitude()) * t,
                9500,
                225,
                90,
                now,
                true));
      }
    return result;
  }
}
