package com.aircampus.model;

public enum FareClass {
  ECONOMY(1, 23, 1.0),
  BUSINESS(2, 32, 1.7);
  private final int pieces;
  private final double kg, multiplier;

  FareClass(int pieces, double kg, double multiplier) {
    this.pieces = pieces;
    this.kg = kg;
    this.multiplier = multiplier;
  }

  public int pieces() {
    return pieces;
  }

  public double kg() {
    return kg;
  }

  public long price(long base) {
    return Math.round(base * multiplier);
  }

  @Override
  public String toString() {
    return name();
  }
}
