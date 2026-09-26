package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.Clock;
import java.util.*;

public final class OperationsService extends ServiceSupport {
  public static final List<String> REASONS =
      List.of("EQUIPMENT", "FUEL", "BAGGAGE", "SAFETY", "WEATHER", "OTHER");

  public OperationsService(Database db, AuthService auth, Clock clock, EventBus events) {
    super(db, auth, clock, events);
  }

  public void reportIncident(
      Session s,
      long id,
      String reason,
      String location,
      long happened,
      String description,
      String severity) {
    auth.require(
        s,
        Role.GROUND,
        Role.SECURITY,
        Role.ATC,
        Role.OPS,
        Role.AIR_CREW,
        Role.MAINTENANCE,
        Role.MANAGER);
    change(
        s,
        "INCIDENT_REPORTED",
        id,
        () -> {
          flight(id);
          Checks.that(REASONS.contains(reason), "Select an incident reason code.");
          Checks.text(location, "Location", 80);
          Checks.that(
              happened > 0 && happened <= now() + 60, "Incident time cannot be in the future.");
          Checks.that(
              List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity),
              "Choose incident severity.");
          Checks.text(description, "Incident description", 500);
          db.update(
              "INSERT INTO"
                  + " incidents(flight_id,category,reason,location,happened_at,description,severity,status,reporter)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              id,
              s.user().role().name(),
              reason,
              location,
              happened,
              description,
              severity,
              List.of("HIGH", "CRITICAL").contains(severity) ? "ESCALATED" : "OPEN",
              s.user().username());
          return null;
        });
  }

  public List<Incident> incidents(Session s) {
    auth.require(
        s,
        Role.GROUND,
        Role.SECURITY,
        Role.ATC,
        Role.OPS,
        Role.AIR_CREW,
        Role.MAINTENANCE,
        Role.MANAGER);
    return db.query("SELECT * FROM incidents ORDER BY id DESC", Mappers.INCIDENT);
  }

  public void resolveIncident(Session s, long id, String resolution) {
    auth.require(s, Role.MANAGER);
    change(
        s,
        "INCIDENT_RESOLVED",
        0,
        () -> {
          Checks.text(resolution, "Resolution", 300);
          Checks.that(
              db.update(
                      "UPDATE incidents SET status='RESOLVED',description=description || ? WHERE"
                          + " id=? AND status<>'RESOLVED'",
                      " | Resolution: " + resolution,
                      id)
                  > 0,
              "Select an unresolved incident.");
          return null;
        });
  }

  public void inspect(Session s, long aircraftId, boolean airworthy, String notes) {
    auth.require(s, Role.MAINTENANCE);
    change(
        s,
        "MAINTENANCE_INSPECTION",
        0,
        () -> {
          aircraft(aircraftId);
          Checks.text(notes, "Inspection / defect notes", 400);
          db.update(
              "INSERT INTO maintenance_log(aircraft_id,created_at,inspector,result,notes)"
                  + " VALUES(?,?,?,?,?)",
              aircraftId,
              now(),
              s.user().username(),
              airworthy ? "AIRWORTHY" : "GROUNDED",
              notes);
          db.update("UPDATE aircraft SET airworthy=? WHERE id=?", airworthy, aircraftId);
          for (Flight f : flights.findAll())
            if (f.aircraftId() == aircraftId && f.status().isOpen()) {
              db.update("UPDATE flights SET preflight=0 WHERE id=?", f.id());
              invalidateLoad(f.id());
            }
          return null;
        });
  }

  public List<String> inspectionLog(Session s) {
    auth.require(s, Role.MAINTENANCE, Role.OPS, Role.MANAGER);
    return db.query(
        "SELECT m.*,a.registration FROM maintenance_log m JOIN aircraft a ON a.id=m.aircraft_id"
            + " ORDER BY m.id DESC",
        r ->
            Formats.time(r.getLong("created_at"))
                + " | "
                + r.getString("registration")
                + " | "
                + r.getString("result")
                + " | "
                + r.getString("inspector")
                + " | "
                + r.getString("notes"));
  }

  public List<AuditEntry> auditLog(Session s) {
    auth.require(s, Role.MANAGER);
    return db.query("SELECT * FROM audit ORDER BY id DESC LIMIT 500", Mappers.AUDIT);
  }

  public List<Claim> claims(Session s) {
    auth.require(s, Role.MANAGER);
    return db.query("SELECT * FROM claims ORDER BY id DESC", Mappers.CLAIM);
  }

  public void updateClaim(Session s, long id, String status) {
    auth.require(s, Role.MANAGER);
    change(
        s,
        "CLAIM_UPDATED",
        0,
        () -> {
          Checks.that(
              List.of("SEARCHING", "FOUND", "RETURNED").contains(status), "Choose a claim status.");
          Claim c =
              db.query("SELECT * FROM claims WHERE id=?", Mappers.CLAIM, id).stream()
                  .findFirst()
                  .orElseThrow(
                      () -> new com.aircampus.exception.ValidationException("Select a claim."));
          boolean allowed =
              (c.status().equals("OPEN") && status.equals("SEARCHING"))
                  || (c.status().equals("SEARCHING") && status.equals("FOUND"))
                  || (c.status().equals("FOUND") && status.equals("RETURNED"));
          Checks.that(allowed, "Claims follow OPEN → SEARCHING → FOUND → RETURNED.");
          db.update("UPDATE claims SET status=? WHERE id=?", status, id);
          notifyUser(c.userId(), "Baggage claim #" + id + ": " + status);
          return null;
        });
  }

  public String report(Session s) {
    auth.require(s, Role.MANAGER);
    List<Flight> day =
        flights.findAll().stream()
            .filter(f -> Formats.date(f.departure()).equals(today()))
            .toList();
    long delayed = day.stream().filter(f -> f.delayMinutes() > 0).count();
    StringBuilder out =
        new StringBuilder("DAILY AIRPORT REPORT — " + today() + " (Cambodia time)\n\n");
    out.append("Scheduled flights today: ")
        .append(day.size())
        .append("\nDelayed flights: ")
        .append(delayed)
        .append(day.isEmpty() ? "" : " (" + Math.round(delayed * 100.0 / day.size()) + "%)")
        .append("\nActive bookings (all dates): ")
        .append(db.count("SELECT COUNT(*) FROM bookings WHERE status<>'CANCELLED'"))
        .append("\nStaff counter sessions: ")
        .append(db.count("SELECT COUNT(*) FROM counters WHERE expires_at>?", auth.leaseNow()))
        .append("\nUnresolved incidents: ")
        .append(db.count("SELECT COUNT(*) FROM incidents WHERE status<>'RESOLVED'"))
        .append("\n\nTODAY'S ROUTES\n");
    Map<String, Long> routes = new TreeMap<>();
    for (Flight f : day)
      routes.merge(f.origin().name() + " > " + f.destination().name(), 1L, Long::sum);
    routes.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(
            e -> out.append(e.getKey()).append(": ").append(e.getValue()).append(" flights\n"));
    out.append("\nGATE ALLOCATIONS\n");
    for (Flight f : day)
      out.append(f.number())
          .append(" | ")
          .append(f.gate().isBlank() ? "Unassigned" : f.gate())
          .append(" | ")
          .append(Formats.time(f.estimatedDeparture()))
          .append(" | ")
          .append(f.status())
          .append("\n");
    out.append("\nCOUNTERS ON DUTY\n");
    for (String line :
        db.query(
            "SELECT c.counter,u.full_name,f.number FROM counters c JOIN users u ON u.id=c.user_id"
                + " JOIN flights f ON f.id=c.flight_id WHERE expires_at>?",
            r -> r.getString(1) + " | " + r.getString(2) + " | " + r.getString(3),
            auth.leaseNow())) out.append(line).append("\n");
    return out.toString();
  }
}
