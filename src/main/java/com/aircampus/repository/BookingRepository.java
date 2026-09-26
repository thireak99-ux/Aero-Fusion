package com.aircampus.repository;

import com.aircampus.model.*;
import java.util.*;

public final class BookingRepository implements Dao<Booking, String> {
  private final Database db;

  public BookingRepository(Database db) {
    this.db = db;
  }

  @Override
  public Optional<Booking> findById(String ref) {
    return db.query("SELECT * FROM bookings WHERE reference=?", Mappers.BOOKING, ref).stream()
        .findFirst();
  }

  @Override
  public List<Booking> findAll() {
    return db.query("SELECT * FROM bookings ORDER BY rowid DESC", Mappers.BOOKING);
  }

  public List<Booking> byUser(long userId) {
    return db.query(
        "SELECT * FROM bookings WHERE user_id=? ORDER BY rowid DESC", Mappers.BOOKING, userId);
  }

  public List<Booking> manifest(long flightId) {
    return db.query(
        "SELECT * FROM bookings WHERE flight_id=? AND status<>'CANCELLED' ORDER BY seat",
        Mappers.BOOKING,
        flightId);
  }
}
