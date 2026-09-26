package com.aircampus.model;

public record Aircraft(
    long id,
    String registration,
    String type,
    int rows,
    double emptyKg,
    double maxTakeoffKg,
    boolean airworthy) {

  @Override
  public String toString() {
    return registration + " / " + type;
  }
}
