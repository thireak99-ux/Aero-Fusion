package com.aircampus.model;

public enum FlightStatus {
  SCHEDULED,
  DELAYED,
  BOARDING,
  DEPARTED,
  IN_AIR,
  LANDED,
  CANCELLED;

  public boolean isOpen() {
    return this == SCHEDULED || this == DELAYED || this == BOARDING;
  }

  @Override
  public String toString() {
    return name().replace('_', ' ');
  }
}
