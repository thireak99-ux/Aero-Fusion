package com.aircampus.repository;

import com.aircampus.model.*;

public final class Mappers {
  private Mappers() {}

  public static final RowMapper<User> USER =
      r -> {
        Role role = Role.valueOf(r.getString("role"));
        return role == Role.PASSENGER
            ? new Passenger(
                r.getLong("id"),
                r.getString("full_name"),
                r.getString("email"),
                r.getString("phone"),
                r.getString("username"))
            : new Staff(
                r.getLong("id"),
                r.getString("full_name"),
                r.getString("email"),
                r.getString("phone"),
                r.getString("username"),
                role);
      };
  public static final RowMapper<Flight> FLIGHT =
      r ->
          new Flight(
              r.getLong("id"),
              r.getString("number"),
              Airport.valueOf(r.getString("origin")),
              Airport.valueOf(r.getString("destination")),
              r.getLong("departure"),
              r.getLong("arrival"),
              r.getInt("delay_minutes"),
              r.getLong("price_cents"),
              r.getLong("aircraft_id"),
              r.getString("gate"),
              FlightStatus.valueOf(r.getString("status")),
              r.getBoolean("preflight"),
              r.getBoolean("weight_checked"),
              r.getBoolean("pushback"),
              r.getDouble("cargo_kg"),
              r.getDouble("fuel_kg"),
              r.getBoolean("emergency"),
              r.getString("callsign"),
              r.getString("icao24"));
  public static final RowMapper<Aircraft> AIRCRAFT =
      r ->
          new Aircraft(
              r.getLong("id"),
              r.getString("registration"),
              r.getString("type"),
              r.getInt("rows"),
              r.getDouble("empty_kg"),
              r.getDouble("max_takeoff_kg"),
              r.getBoolean("airworthy"));
  public static final RowMapper<Booking> BOOKING =
      r ->
          new Booking(
              r.getString("reference"),
              r.getLong("user_id"),
              r.getLong("flight_id"),
              r.getString("passenger_name"),
              r.getString("passport"),
              r.getString("date_of_birth"),
              r.getString("guardian_reference"),
              r.getString("guardian_name"),
              r.getString("guardian_phone"),
              r.getString("seat"),
              FareClass.valueOf(r.getString("fare_class")),
              r.getString("status"),
              r.getLong("paid_cents"),
              r.getInt("baggage_pieces"),
              r.getDouble("baggage_kg"),
              r.getLong("baggage_fee_cents"),
              r.getBoolean("identity_verified"),
              r.getString("passenger_screen"),
              r.getString("baggage_screen"),
              r.getBoolean("gate_cleared"),
              r.getString("assistance"),
              r.getString("meal"),
              r.getLong("refund_cents"));
  public static final RowMapper<GroundTask> TASK =
      r ->
          new GroundTask(
              r.getLong("id"),
              r.getLong("flight_id"),
              r.getString("name"),
              r.getString("status"),
              r.getString("assigned_team"));
  public static final RowMapper<CrewMember> CREW =
      r ->
          new CrewMember(
              r.getLong("id"),
              r.getString("name"),
              r.getString("duty"),
              r.getLong("certified_until"),
              r.getString("aircraft_type"));
  public static final RowMapper<RunwaySlot> SLOT =
      r ->
          new RunwaySlot(
              r.getLong("id"),
              r.getLong("flight_id"),
              r.getString("runway"),
              r.getLong("start_time"),
              r.getLong("end_time"),
              r.getString("operation"),
              r.getString("state"),
              r.getLong("queued_at"));
  public static final RowMapper<Notice> NOTICE =
      r ->
          new Notice(
              r.getLong("id"),
              r.getLong("user_id"),
              r.getLong("created_at"),
              r.getString("message"),
              r.getBoolean("is_read"));
  public static final RowMapper<AuditEntry> AUDIT =
      r ->
          new AuditEntry(
              r.getLong("id"),
              r.getLong("created_at"),
              r.getString("actor"),
              r.getString("action"),
              r.getString("details"));
  public static final RowMapper<Incident> INCIDENT =
      r ->
          new Incident(
              r.getLong("id"),
              r.getLong("flight_id"),
              r.getString("category"),
              r.getString("reason"),
              r.getString("location"),
              r.getLong("happened_at"),
              r.getString("description"),
              r.getString("severity"),
              r.getString("status"),
              r.getString("reporter"));
  public static final RowMapper<Claim> CLAIM =
      r ->
          new Claim(
              r.getLong("id"),
              r.getLong("user_id"),
              r.getString("reference"),
              r.getString("description"),
              r.getString("status"));
}
