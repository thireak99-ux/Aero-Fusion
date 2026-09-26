package com.aircampus.model;

public final class Passenger extends User {
  public Passenger(long id, String name, String email, String phone, String username) {
    super(id, name, email, phone, username, Role.PASSENGER);
  }

  @Override
  public String welcome() {
    return "Your next journey starts here.";
  }
}
