package com.aircampus.model;

public record RunwaySlot(
    long id,
    long flightId,
    String runway,
    long startTime,
    long endTime,
    String operation,
    String state,
    long queuedAt) {}
