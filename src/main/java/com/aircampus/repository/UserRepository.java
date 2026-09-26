package com.aircampus.repository;

import com.aircampus.model.*;
import java.util.*;

public final class UserRepository implements Dao<User, Long> {
  private final Database db;

  public UserRepository(Database db) {
    this.db = db;
  }

  @Override
  public Optional<User> findById(Long id) {
    return db.query("SELECT * FROM users WHERE id=?", Mappers.USER, id).stream().findFirst();
  }

  @Override
  public List<User> findAll() {
    return db.query("SELECT * FROM users ORDER BY role,full_name", Mappers.USER);
  }

  public Optional<User> byLogin(String value) {
    return db
        .query(
            "SELECT * FROM users WHERE email=? COLLATE NOCASE OR username=? COLLATE NOCASE",
            Mappers.USER,
            value,
            value)
        .stream()
        .findFirst();
  }
}
