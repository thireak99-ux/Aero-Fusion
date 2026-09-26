package com.aircampus.model;

public record Booking(
    String reference,
    long userId,
    long flightId,
    String passengerName,
    String passport,
    String dateOfBirth,
    String guardianReference,
    String guardianName,
    String guardianPhone,
    String seat,
    FareClass fareClass,
    String status,
    long paidCents,
    int baggagePieces,
    double baggageKg,
    long baggageFeeCents,
    boolean identityVerified,
    String passengerScreen,
    String baggageScreen,
    boolean gateCleared,
    String assistance,
    String meal,
    long refundCents) {

  public boolean active() {
    return !status.equals("CANCELLED");
  }

  public boolean checkedIn() {
    return status.equals("CHECKED_IN");
  }

  @Override
  public String toString() {
    return reference + " | " + passengerName + " | " + seat;
  }
}
