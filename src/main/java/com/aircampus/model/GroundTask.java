package com.aircampus.model;

public record GroundTask(long id, long flightId, String name, String status, String assignedTeam) {}
