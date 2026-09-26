package com.aircampus.api;

import com.aircampus.model.Position;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;

/** Network work runs on TrackingService's background executor, never on the JavaFX thread. */
public final class OpenSkyProvider implements FlightDataProvider {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private String token = "";
  private long expiresAt;

  private String bearer() throws Exception {
    String id = System.getenv("OPENSKY_CLIENT_ID"), secret = System.getenv("OPENSKY_CLIENT_SECRET");
    if (id == null || id.isBlank() || secret == null || secret.isBlank()) return "";
    if (Instant.now().getEpochSecond() < expiresAt) return token;
    String body =
        "grant_type=client_credentials&client_id="
            + URLEncoder.encode(id, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(secret, StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"))
            .timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200)
      throw new java.io.IOException("OpenSky authentication HTTP " + response.statusCode());
    JSONObject json = new JSONObject(response.body());
    token = json.getString("access_token");
    expiresAt = Instant.now().getEpochSecond() + json.optLong("expires_in", 1800) - 60;
    return token;
  }

  @Override
  public List<Position> fetch() throws Exception {
    // A regional bounding box saves bandwidth and credits. Demo airport routes are in SE Asia.
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create(
                    "https://opensky-network.org/api/states/all?lamin=0&lomin=99&lamax=23&lomax=108"))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json");
    String auth = bearer();
    if (!auth.isEmpty()) request.header("Authorization", "Bearer " + auth);
    HttpResponse<String> response =
        client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 401) expiresAt = 0;
    if (response.statusCode() != 200)
      throw new java.io.IOException("OpenSky HTTP " + response.statusCode());
    return parse(response.body());
  }

  private static double finite(double value) {
    return Double.isFinite(value) ? value : 0;
  }

  public static List<Position> parse(String body) {
    JSONObject json = new JSONObject(body);
    JSONArray states = json.optJSONArray("states");
    List<Position> positions = new ArrayList<>();
    if (states == null) return positions;
    long time = json.optLong("time", Instant.now().getEpochSecond());
    for (int i = 0; i < states.length(); i++) {
      JSONArray a = states.optJSONArray(i);
      if (a == null || a.length() < 11 || a.isNull(5) || a.isNull(6) || a.optBoolean(8, false))
        continue;
      double lon = a.optDouble(5, Double.NaN), lat = a.optDouble(6, Double.NaN);
      if (!Double.isFinite(lon)
          || !Double.isFinite(lat)
          || Math.abs(lon) > 180
          || Math.abs(lat) > 90) continue;
      positions.add(
          new Position(
              a.optString(0, ""),
              a.isNull(1) ? "" : a.optString(1, "").trim(),
              lat,
              lon,
              finite(a.optDouble(7, 0)),
              finite(a.optDouble(9, 0)),
              finite(a.optDouble(10, 0)),
              a.isNull(3) ? time : a.optLong(3, time),
              false));
    }
    return positions;
  }
}
