package com.aircampus.model;

public record Flight(
    long id,
    String number,
    Airport origin,
    Airport destination,
    long departure,
    long arrival,
    int delayMinutes,
    long priceCents,
    long aircraftId,
    String gate,
    FlightStatus status,
    boolean preflight,
    boolean weightChecked,
    boolean pushback,
    double cargoKg,
    double fuelKg,
    boolean emergency,
    String callsign,
    String icao24) {

  public long estimatedDeparture() {
    return departure + delayMinutes * 60L;
  }

  public long estimatedArrival() {
    return arrival + delayMinutes * 60L;
  }

  @Override
  public String toString() {
    return number
        + " | "
        + origin.name()
        + " > "
        + destination.name()
        + " | "
        + com.aircampus.util.Formats.time(estimatedDeparture());
  }
}
