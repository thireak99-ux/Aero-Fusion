package com.aircampus.service;

import com.aircampus.model.*;
import com.aircampus.util.*;

public record FlightDraft(
    String number,
    Airport origin,
    Airport destination,
    long departure,
    long arrival,
    long priceCents,
    long aircraftId,
    String callsign,
    String icao24)
    implements Validatable {
  @Override
  public void validate() {
    Checks.that(
        number != null && number.matches("[A-Z0-9]{2,3}[0-9]{1,4}"),
        "Flight number needs an airline prefix and 1–4 digits, for example AC101.");
    Checks.required(origin, "origin");
    Checks.required(destination, "destination");
    Checks.that(origin != destination, "Origin and destination must differ.");
    Checks.that(
        departure < arrival && arrival - departure <= 86400,
        "Arrival must follow departure, within 24 hours.");
    Checks.that(
        priceCents > 0 && priceCents <= 1_000_000, "Price must be between $0.01 and $10,000.");
    Checks.that(aircraftId > 0, "Select an aircraft.");
    Checks.that(
        callsign != null && (callsign.isEmpty() || callsign.matches("[A-Z0-9]{2,8}")),
        "Callsign must have 2–8 letters/digits or be empty.");
    Checks.that(
        icao24 != null && (icao24.isEmpty() || icao24.matches("[0-9a-f]{6}")),
        "ICAO24 must have exactly 6 hexadecimal characters or be empty.");
  }
}
