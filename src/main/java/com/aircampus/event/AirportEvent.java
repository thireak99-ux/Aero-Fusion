package com.aircampus.event;

public record AirportEvent(String topic, long flightId, String message) {}
