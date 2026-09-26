package com.aircampus.service;

import com.aircampus.model.FareClass;
import com.aircampus.util.*;
import java.time.LocalDate;

public record BookingRequest(
    long flightId,
    String name,
    String idName,
    String passport,
    LocalDate dateOfBirth,
    String guardianReference,
    String guardianName,
    String guardianPhone,
    String seat,
    FareClass fareClass,
    String assistance,
    String meal)
    implements Validatable {
  @Override
  public void validate() {
    Checks.that(flightId > 0, "Choose a flight.");
    Checks.name(name);
    Checks.name(idName);
    Checks.that(
        Checks.normalizedName(name).equals(Checks.normalizedName(idName)),
        "Passenger name must match the name on the ID.");
    Checks.passport(passport);
    Checks.required(dateOfBirth, "date of birth");
    Checks.required(fareClass, "fare class");
    Checks.text(seat, "Seat", 4);
    Checks.that(
        java.util.List.of("None", "Wheelchair", "Unaccompanied minor").contains(assistance),
        "Choose an offered assistance option.");
    Checks.that(
        java.util.List.of("Standard", "Vegetarian", "Halal").contains(meal),
        "Choose an offered meal.");
  }
}
