package com.aircampus.repository;

import com.aircampus.exception.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

/** SQL business rules run in memory; durable records are UTF-8 CSV snapshots. */
public final class Database implements AutoCloseable {
  private final Connection connection;
  private final CsvSnapshots csv;
  private boolean ready;
  private boolean dirty;

  public Database(Path file) {
    CsvSnapshots snapshots = null;
    Connection opened = null;
    try {
      snapshots = new CsvSnapshots(file);
      opened = DriverManager.getConnection("jdbc:sqlite::memory:");
      csv = snapshots;
      connection = opened;
      try (Statement s = connection.createStatement()) {
        s.execute("PRAGMA foreign_keys=ON");
      }
      try (var in =
          Objects.requireNonNull(Database.class.getResourceAsStream("/com/aircampus/schema.sql"))) {
        for (String sql : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";"))
          if (!sql.isBlank()) update(sql);
      }
      csv.load(connection);
      ready = true;
    } catch (Exception e) {
      if (opened != null) try { opened.close(); } catch (SQLException close) { e.addSuppressed(close); }
      if (snapshots != null) try { snapshots.close(); } catch (Exception close) { e.addSuppressed(close); }
      throw new DataAccessException(
          "Cannot read the airport CSV files. Check that the data folder is writable.", e);
    }
  }

  private PreparedStatement statement(String sql, Object... args) throws SQLException {
    PreparedStatement p = connection.prepareStatement(sql);
    for (int i = 0; i < args.length; i++)
      p.setObject(i + 1, args[i] instanceof Enum<?> en ? en.name() : args[i]);
    return p;
  }

  public synchronized int update(String sql, Object... args) {
    try (PreparedStatement p = statement(sql, args)) {
      int result = p.executeUpdate();
      if (ready) {
        dirty = true;
        if (connection.getAutoCommit()) save();
      }
      return result;
    } catch (SQLException e) {
      throw translate(e);
    }
  }

  public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
    try (PreparedStatement p = statement(sql, args);
        ResultSet r = p.executeQuery()) {
      List<T> rows = new ArrayList<>();
      while (r.next()) rows.add(mapper.map(r));
      return rows;
    } catch (SQLException e) {
      throw translate(e);
    }
  }

  public long count(String sql, Object... args) {
    return query(sql, r -> r.getLong(1), args).get(0);
  }

  public synchronized <T> T transaction(Supplier<T> work) {
    boolean owner = false;
    boolean committed = false;
    try {
      owner = connection.getAutoCommit();
      if (owner) connection.setAutoCommit(false);
      T result = work.get();
      if (owner) {
        connection.commit();
        committed = true;
        save();
      }
      return result;
    } catch (RuntimeException | SQLException e) {
      if (owner && !committed)
        try {
          connection.rollback();
          dirty = false;
        } catch (SQLException rollback) {
          e.addSuppressed(rollback);
        }
      if (e instanceof RuntimeException runtime) throw runtime;
      throw new DataAccessException("Database transaction failed; no changes were saved.", e);
    } finally {
      if (owner)
        try {
          connection.setAutoCommit(true);
        } catch (SQLException e) {
          throw new DataAccessException("Could not restore the database connection.", e);
        }
    }
  }

  private void save() {
    if (!dirty) return;
    csv.save(connection);
    dirty = false;
  }

  public Path csvDirectory() {
    return csv.directory();
  }

  private AppException translate(SQLException e) {
    String m = String.valueOf(e.getMessage());
    if (m.contains("bookings.flight_id, bookings.seat"))
      return new SeatUnavailableException("That seat was just taken. Please choose another.", e);
    if (m.contains("UNIQUE"))
      return new ConflictException(
          "This record already exists (email, username, flight number or passenger).", e);
    if (m.contains("SQLITE_BUSY"))
      return new ConflictException("Another window is saving. Refresh and try again.", e);
    return new DataAccessException("A record could not be saved or read.", e);
  }

  @Override
  public synchronized void close() {
    try {
      save();
    } finally {
      try {
        connection.close();
      } catch (SQLException e) {
        throw new DataAccessException("Could not close the airport store.", e);
      } finally {
        try { csv.close(); }
        catch (java.io.IOException e) { throw new DataAccessException("Could not release the CSV workspace.", e); }
      }
    }
  }
}
