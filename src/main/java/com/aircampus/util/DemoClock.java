package com.aircampus.util;

import java.time.*;
import java.util.Objects;

/** Uses the computer clock normally. A manager can freeze a teaching scenario in memory. */
public final class DemoClock extends Clock {
  private final Clock realClock;
  private volatile Instant frozen;

  public DemoClock(Clock realClock) {
    this.realClock = Objects.requireNonNull(realClock);
  }

  @Override
  public Instant instant() {
    Instant point = frozen;
    return point == null ? realClock.instant() : point;
  }

  @Override
  public ZoneId getZone() {
    return realClock.getZone();
  }

  @Override
  public Clock withZone(ZoneId zone) {
    Objects.requireNonNull(zone);
    if (zone.equals(getZone())) return this;
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return zone;
      }

      @Override
      public Instant instant() {
        return DemoClock.this.instant();
      }

      @Override
      public Clock withZone(ZoneId other) {
        return DemoClock.this.withZone(other);
      }
    };
  }

  public boolean isFrozen() {
    return frozen != null;
  }

  public void freeze(Instant point) {
    frozen = Objects.requireNonNull(point);
  }

  public void useRealTime() {
    frozen = null;
  }

  public String label() {
    return (isFrozen() ? "DEMO TIME (paused): " : "Current time: ")
        + java.time.format.DateTimeFormatter.ofPattern(
                "dd MMM yyyy, HH:mm", java.util.Locale.ENGLISH)
            .withZone(Formats.ZONE)
            .format(instant());
  }
}
