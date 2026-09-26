package com.aircampus.model;

public record CrewMember(
    long id, String name, String duty, long certifiedUntil, String aircraftType) {

  @Override
  public String toString() {
    return name + " / " + duty;
  }
}
