package com.aircampus.model;

public final class Staff extends User {
  public Staff(long id, String name, String email, String phone, String username, Role role) {
    super(id, name, email, phone, username, role);
  }

  @Override
  public String welcome() {
    return "Your " + role() + " workspace is ready.";
  }
}
