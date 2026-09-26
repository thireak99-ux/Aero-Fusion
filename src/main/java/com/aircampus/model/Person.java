package com.aircampus.model;

public abstract class Person {
  private final long id;
  private final String fullName, email, phone;

  protected Person(long id, String fullName, String email, String phone) {
    this.id = id;
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
  }

  public long id() {
    return id;
  }

  public String fullName() {
    return fullName;
  }

  public String email() {
    return email;
  }

  public String phone() {
    return phone;
  }
}
