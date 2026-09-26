package com.aircampus.repository;

import com.aircampus.exception.DataAccessException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Saves each application table as a readable UTF-8 CSV file. */
final class CsvSnapshots implements AutoCloseable {
  private static final List<String> TABLES =
      List.of(
          "users", "aircraft", "crew", "flights", "flight_crew", "bookings",
          "ground_tasks", "counters", "runway_slots", "notifications", "audit",
          "watchlist", "incidents", "maintenance_log", "claims", "settings",
          "demo_batches", "demo_flights");

  private final Path root;
  private final Path legacyDb;
  private final FileChannel lockChannel;
  private final FileLock lock;

  CsvSnapshots(Path requested) throws IOException {
    String name = requested.getFileName().toString();
    if (name.endsWith(".db")) {
      root = requested.resolveSibling(name.substring(0, name.length() - 3) + "-csv");
      legacyDb = requested;
    } else {
      root = requested;
      legacyDb = requested.resolveSibling("airport.db");
    }
    Files.createDirectories(root);
    lockChannel = FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock acquired;
    try { acquired = lockChannel.tryLock(); }
    catch (OverlappingFileLockException e) {
      lockChannel.close();
      throw new IOException("This CSV workspace is already open in another application.", e);
    }
    if (acquired == null) {
      lockChannel.close();
      throw new IOException("This CSV workspace is already open in another process.");
    }
    lock = acquired;
  }

  Path directory() {
    return root.resolve("current");
  }

  void load(Connection destination) throws SQLException, IOException {
    Path current = directory();
    Path previous = root.resolve("previous");
    if (!Files.isDirectory(current) && Files.isDirectory(previous)) {
      Files.createDirectories(root);
      Files.move(previous, current);
    }
    if (Files.isDirectory(current)) {
      for (String table : TABLES) {
        Path file = current.resolve(table + ".csv");
        if (Files.exists(file)) importCsv(destination, table, file);
      }
    } else if (Files.isRegularFile(legacyDb)) {
      // Import the earlier AeroFusion database once, leaving the original untouched.
      try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + legacyDb.toAbsolutePath())) {
        for (String table : TABLES) {
          if (!exists(source, table)) continue;
          List<String> columns = columns(source, table);
          try (Statement statement = source.createStatement();
              ResultSet rows = statement.executeQuery("SELECT * FROM " + table)) {
            insertRows(destination, table, columns, rows);
          }
        }
      }
      save(destination);
    }
  }

  void save(Connection source) {
    Path stage = null;
    try {
      Files.createDirectories(root);
      stage = Files.createTempDirectory(root, "next-");
      for (String table : TABLES) {
        if (!exists(source, table)) continue;
        try (Statement statement = source.createStatement();
            ResultSet rows = statement.executeQuery("SELECT * FROM " + table)) {
          ResultSetMetaData meta = rows.getMetaData();
          StringBuilder csv = new StringBuilder();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (i > 1) csv.append(',');
            cell(csv, meta.getColumnName(i));
          }
          csv.append('\n');
          while (rows.next()) {
            for (int i = 1; i <= meta.getColumnCount(); i++) {
              if (i > 1) csv.append(',');
            String value = rows.getString(i);
            if (value == null) csv.append("NULL");
            else cell(csv, value);
            }
            csv.append('\n');
          }
          Files.writeString(stage.resolve(table + ".csv"), csv, StandardCharsets.UTF_8);
        }
      }
      Path previous = root.resolve("previous");
      Path current = directory();
      removeTree(previous);
      if (Files.exists(current)) Files.move(current, previous);
      Files.move(stage, current);
      stage = null;
    } catch (SQLException | IOException e) {
      throw new DataAccessException("Could not save CSV files in " + root, e);
    } finally {
      if (stage != null)
        try {
          removeTree(stage);
        } catch (IOException ignored) {
          // The last complete snapshot remains in current or previous.
        }
    }
  }

  private static boolean exists(Connection c, String table) throws SQLException {
    try (PreparedStatement p = c.prepareStatement(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")) {
      p.setString(1, table);
      try (ResultSet r = p.executeQuery()) {
        return r.next() && r.getInt(1) > 0;
      }
    }
  }

  private static List<String> columns(Connection c, String table) throws SQLException {
    List<String> names = new ArrayList<>();
    try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (r.next()) names.add(r.getString("name"));
    }
    return names;
  }

  private static void importCsv(Connection c, String table, Path file) throws SQLException, IOException {
    List<List<CsvValue>> rows = parse(Files.readString(file, StandardCharsets.UTF_8));
    if (rows.isEmpty()) return;
    List<String> header = rows.get(0).stream().map(CsvValue::value).toList();
    List<String> allowed = columns(c, table);
    if (!allowed.containsAll(header))
      throw new IOException("Unexpected column in " + file.getFileName());
    String sql = "INSERT OR REPLACE INTO " + table + " (" + String.join(",", header) + ") VALUES ("
        + String.join(",", Collections.nCopies(header.size(), "?")) + ")";
    try (PreparedStatement p = c.prepareStatement(sql)) {
      for (int j = 1; j < rows.size(); j++) {
        List<CsvValue> row = rows.get(j);
        if (row.size() != header.size()) throw new IOException("Invalid CSV row in " + file);
        for (int i = 0; i < row.size(); i++) {
          CsvValue value = row.get(i);
          if (!value.quoted() && value.value().equals("NULL")) p.setNull(i + 1, Types.NULL);
          else p.setString(i + 1, value.value());
        }
        p.executeUpdate();
      }
    }
  }

  private static void insertRows(Connection dest, String table, List<String> columns, ResultSet rows)
      throws SQLException {
    String sql = "INSERT OR REPLACE INTO " + table + " (" + String.join(",", columns) + ") VALUES ("
        + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
    try (PreparedStatement p = dest.prepareStatement(sql)) {
      while (rows.next()) {
        for (int i = 0; i < columns.size(); i++) p.setObject(i + 1, rows.getObject(columns.get(i)));
        p.executeUpdate();
      }
    }
  }

  private static void cell(StringBuilder out, String value) {
    out.append('"').append(value == null ? "" : value.replace("\"", "\"\"")) .append('"');
  }

  /** RFC 4180-style quoted values, including commas, quotes and embedded newlines. */
  private record CsvValue(String value, boolean quoted) {}

  private static List<List<CsvValue>> parse(String data) throws IOException {
    List<List<CsvValue>> result = new ArrayList<>();
    List<CsvValue> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    boolean fieldQuoted = false;
    for (int i = 0; i < data.length(); i++) {
      char ch = data.charAt(i);
      if (ch == '"') {
        if (quoted && i + 1 < data.length() && data.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else { if (!quoted) fieldQuoted = true; quoted = !quoted; }
      } else if (ch == ',' && !quoted) {
        row.add(new CsvValue(field.toString(), fieldQuoted)); field.setLength(0); fieldQuoted = false;
      } else if ((ch == '\n' || ch == '\r') && !quoted) {
        if (ch == '\r' && i + 1 < data.length() && data.charAt(i + 1) == '\n') i++;
        row.add(new CsvValue(field.toString(), fieldQuoted)); field.setLength(0); fieldQuoted = false;
        result.add(row); row = new ArrayList<>();
      } else field.append(ch);
    }
    if (quoted) throw new IOException("CSV ends inside a quoted value");
    if (!row.isEmpty() || field.length() > 0) {
      row.add(new CsvValue(field.toString(), fieldQuoted)); result.add(row);
    }
    return result;
  }

  private static void removeTree(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(item);
    }
  }

  @Override
  public void close() throws IOException {
    lock.release();
    lockChannel.close();
  }
}
