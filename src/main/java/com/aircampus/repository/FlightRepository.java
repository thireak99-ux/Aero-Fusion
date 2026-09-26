package com.aircampus.repository;

import com.aircampus.model.*;
import java.util.*;

public final class FlightRepository implements Dao<Flight, Long> {
  private final Database db;

  public FlightRepository(Database db) {
    this.db = db;
  }

  @Override
  public Optional<Flight> findById(Long id) {
    return db.query("SELECT * FROM flights WHERE id=?", Mappers.FLIGHT, id).stream().findFirst();
  }

  @Override
  public List<Flight> findAll() {
    return db.query("SELECT * FROM flights ORDER BY departure", Mappers.FLIGHT);
  }
}
