package com.aircampus.service;

import com.aircampus.event.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.*;
import java.time.Clock;
import java.util.*;

public final class GroundService extends ServiceSupport {
  public GroundService(Database db, AuthService auth, Clock clock, EventBus events) {
    super(db, auth, clock, events);
  }

  public List<GroundTask> tasks(Session s, long id) {
    auth.require(s);
    flight(id);
    return db.query("SELECT * FROM ground_tasks WHERE flight_id=? ORDER BY id", Mappers.TASK, id);
  }

  public void advance(Session s, long taskId) {
    auth.require(s, Role.GROUND);
    change(
        s,
        "GROUND_TASK",
        0,
        () -> {
          GroundTask task =
              db.query("SELECT * FROM ground_tasks WHERE id=?", Mappers.TASK, taskId).stream()
                  .findFirst()
                  .orElseThrow(
                      () -> new com.aircampus.exception.ValidationException("Select a task."));
          mutableFlight(flight(task.flightId()));
          Checks.that(!task.status().equals("DONE"), "This task is already complete.");
          if (task.name().equals("Baggage loading") || task.name().equals("Cleaning"))
            Checks.that(
                tasks(s, task.flightId()).stream()
                    .anyMatch(
                        t -> t.name().equals("Baggage unloading") && t.status().equals("DONE")),
                "Complete baggage unloading first.");
          if (task.name().equals("Catering"))
            Checks.that(
                tasks(s, task.flightId()).stream()
                    .anyMatch(t -> t.name().equals("Cleaning") && t.status().equals("DONE")),
                "Complete cleaning before catering.");
          db.update(
              "UPDATE ground_tasks SET status=? WHERE id=?",
              task.status().equals("NOT_STARTED") ? "IN_PROGRESS" : "DONE",
              taskId);
          return null;
        });
  }

  public void pushback(Session s, long id) {
    auth.require(s, Role.GROUND);
    change(
        s,
        "PUSHBACK_REQUESTED",
        id,
        () -> {
          Flight f = flight(id);
          mutableFlight(f);
          Checks.that(
              tasks(s, id).size() == FlightService.TASKS.size()
                  && tasks(s, id).stream().allMatch(t -> t.status().equals("DONE")),
              "Complete every mandatory turnaround task before requesting pushback.");
          Checks.that(aircraft(f.aircraftId()).airworthy(), "Aircraft is grounded by maintenance.");
          Checks.that(!f.gate().isBlank(), "Operations must assign a gate first.");
          Checks.that(!f.pushback(), "Pushback is already requested.");
          db.update("UPDATE flights SET pushback=1 WHERE id=?", id);
          db.update(
              "INSERT INTO runway_slots(flight_id,operation,queued_at) VALUES(?,'TAKEOFF',?) ON"
                  + " CONFLICT(flight_id) DO UPDATE SET"
                  + " operation='TAKEOFF',state='QUEUED',runway='',start_time=0,end_time=0",
              id,
              now());
          return null;
        });
  }
}
