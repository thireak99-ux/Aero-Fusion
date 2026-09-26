package com.aircampus.repository;

import com.aircampus.model.*;
import com.aircampus.service.*;
import java.time.*;

/** Initial demo accounts and fleet. DemoService generates current schedules separately. */
public final class DemoData {
  private DemoData() {}

  public static void seed(Database db, Clock clock) {
    if (db.count("SELECT COUNT(*) FROM users") > 0) return;
    db.transaction(
        () -> {
          for (Role r : Role.values())
            db.update(
                "INSERT INTO users(full_name,email,phone,username,role,password_hash)"
                    + " VALUES(?,?,?,?,?,?)",
                r == Role.PASSENGER ? "Demo Passenger" : "Demo " + r,
                r.demoEmail(),
                "+85512345678",
                r.view(),
                r,
                PasswordHasher.hash("Airport123"));
          long base = clock.instant().getEpochSecond() / 60 * 60;
          for (int i = 1; i <= 8; i++)
            db.update(
                "INSERT INTO aircraft VALUES(?,?,?,?,?,?,?)",
                i,
                "DEMO-" + i,
                "A320",
                12,
                42000,
                77000,
                0);
          for (int i = 1; i <= 12; i++) {
            String duty =
                switch ((i - 1) % 3) {
                  case 0 -> "PILOT";
                  case 1 -> "CO_PILOT";
                  default -> "CABIN";
                };
            db.update(
                "INSERT INTO crew VALUES(?,?,?,?,?)",
                i,
                "Crew Member " + i,
                duty,
                base + 365L * 86400,
                "A320");
          }
          db.update(
              "INSERT INTO watchlist(name,reason) VALUES('test watchlist','Fictional name for the"
                  + " security demonstration')");
          return null;
        });
  }
}
