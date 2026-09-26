package com.aircampus.model;

public abstract class User extends Person {
  private final String username;
  private final Role role;

  protected User(long id, String name, String email, String phone, String username, Role role) {
    super(id, name, email, phone);
    this.username = username;
    this.role = role;
  }

  public String username() {
    return username;
  }

  public Role role() {
    return role;
  }

  public abstract String welcome();

  @Override
  public String toString() {
    return fullName() + " (" + role + ")";
  }
}
