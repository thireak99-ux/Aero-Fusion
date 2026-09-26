package com.aircampus.model;

public enum Airport {
  KTI("Phnom Penh", 11.36, 104.92),
  SAI("Siem Reap", 13.37, 104.22),
  BKK("Bangkok", 13.69, 100.75),
  SIN("Singapore", 1.36, 103.99),
  KUL("Kuala Lumpur", 2.74, 101.71),
  SGN("Ho Chi Minh City", 10.82, 106.65),
  HAN("Hanoi", 21.22, 105.81);
  private final String city;
  private final double lat, lon;

  Airport(String city, double lat, double lon) {
    this.city = city;
    this.lat = lat;
    this.lon = lon;
  }

  public double latitude() {
    return lat;
  }

  public double longitude() {
    return lon;
  }

  @Override
  public String toString() {
    return name() + " - " + city;
  }
}
