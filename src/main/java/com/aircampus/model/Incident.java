package com.aircampus.model;

public record Incident(
    long id,
    long flightId,
    String category,
    String reason,
    String location,
    long happenedAt,
    String description,
    String severity,
    String status,
    String reporter) {}
