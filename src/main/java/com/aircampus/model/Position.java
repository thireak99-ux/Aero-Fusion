package com.aircampus.model;

public record Position(
    String icao24,
    String callsign,
    double latitude,
    double longitude,
    double altitudeMeters,
    double speedMetersSecond,
    double heading,
    long observedAt,
    boolean simulated) {}
