package com.aircampus.model;

public enum Role {
  PASSENGER("Passenger", "passenger", "#2563eb"),
  CHECK_IN("Check-in Crew", "checkin", "#0f766e"),
  GROUND("Ground Crew", "ground", "#b45309"),
  SECURITY("Security", "security", "#b91c1c"),
  ATC("Air Traffic Control", "atc", "#0369a1"),
  OPS("Airline Operations", "ops", "#6d28d9"),
  AIR_CREW("Pilot / Cabin Crew", "crew", "#4338ca"),
  MAINTENANCE("Maintenance Crew", "maintenance", "#4d7c0f"),
  MANAGER("Airport Manager", "manager", "#0f172a");
  private final String label, view, color;

  Role(String label, String view, String color) {
    this.label = label;
    this.view = view;
    this.color = color;
  }

  public String view() {
    return view;
  }

  public String color() {
    return color;
  }

  public String demoEmail() {
    return view + "@aircampus.test";
  }

  @Override
  public String toString() {
    return label;
  }
}
