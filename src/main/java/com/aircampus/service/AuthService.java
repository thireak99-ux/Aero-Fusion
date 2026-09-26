package com.aircampus.service;

import com.aircampus.exception.*;
import com.aircampus.model.*;
import com.aircampus.repository.*;
import com.aircampus.util.Checks;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {
  private final Database db;
  private final UserRepository users;
  private final Clock clock;
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  public AuthService(Database db, Clock clock) {
    this.db = db;
    this.clock = clock;
    users = new UserRepository(db);
  }

  public User register(String name, String email, String username, String phone, String password) {
    String n = Checks.name(name),
        e = Checks.email(email),
        u = Checks.username(username),
        p = Checks.phone(phone);
    String hash = PasswordHasher.hash(Checks.password(password));
    return db.transaction(
        () -> {
          db.update(
              "INSERT INTO users(full_name,email,phone,username,role,password_hash)"
                  + " VALUES(?,?,?,?,?,?)",
              n,
              e,
              p,
              u,
              Role.PASSENGER,
              hash);
          return users.byLogin(e).orElseThrow();
        });
  }

  public Session login(String login, String password, Role role) {
    String value = Checks.text(login, "Email / username", 120);
    Checks.required(role, "role");
    User user =
        users
            .byLogin(value)
            .orElseThrow(
                () -> new ValidationException("Email, password or selected role is incorrect."));
    String hash =
        db.query("SELECT password_hash FROM users WHERE id=?", r -> r.getString(1), user.id())
            .get(0);
    if (!PasswordHasher.verify(password, hash) || user.role() != role)
      throw new ValidationException("Email, password or selected role is incorrect.");
    Session session = new Session(UUID.randomUUID().toString(), user);
    sessions.put(session.token(), session);
    return session;
  }

  public void require(Session session, Role... roles) {
    if (session == null || sessions.get(session.token()) != session)
      throw new SecurityCheckException("Your session ended. Please log in again.");
    if (roles.length > 0 && Arrays.stream(roles).noneMatch(r -> r == session.user().role()))
      throw new SecurityCheckException("Your role cannot perform this action.");
  }

  public void logout(Session session) {
    if (session != null) {
      db.update("DELETE FROM counters WHERE session_token=?", session.token());
      sessions.remove(session.token());
    }
  }

  public void heartbeat(Session session) {
    require(session);
    db.update(
        "UPDATE counters SET expires_at=? WHERE session_token=?",
        leaseNow() + 120,
        session.token());
  }

  /** Counter leases follow elapsed computer time, even when a training flight is paused. */
  public long leaseNow() {
    return clock.instant().getEpochSecond();
  }

  public List<User> users(Session session) {
    require(session, Role.MANAGER);
    return users.findAll();
  }
}
