package com.aircampus.api;

import com.aircampus.model.Position;
import java.util.*;
import java.util.concurrent.*;

public final class TrackingService implements AutoCloseable {
  public record Snapshot(List<Position> positions, String label, boolean live, long fetchedAt) {}

  private final FlightDataProvider live, mock;
  private final ExecutorService worker;
  private volatile Snapshot snapshot =
      new Snapshot(List.of(), "Demo tracking — awaiting refresh", false, 0);
  private volatile boolean liveMode;
  private long lastAttempt;
  private CompletableFuture<Snapshot> inFlight;

  public TrackingService(FlightDataProvider live, FlightDataProvider mock) {
    this.live = live;
    this.mock = mock;
    worker =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "flight-tracking");
              t.setDaemon(true);
              return t;
            });
  }

  public boolean isLiveMode() {
    return liveMode;
  }

  public synchronized void setLiveMode(boolean enabled) {
    if (liveMode != enabled) {
      liveMode = enabled;
      lastAttempt = 0;
    }
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public synchronized CompletableFuture<Snapshot> refresh() {
    if (inFlight != null && !inFlight.isDone()) return inFlight;
    long now = System.currentTimeMillis();
    if (liveMode && now - lastAttempt < 120_000) return CompletableFuture.completedFuture(snapshot);
    lastAttempt = now;
    boolean requestedLive = liveMode;
    inFlight =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                List<Position> positions = (requestedLive ? live : mock).fetch();
                snapshot =
                    new Snapshot(
                        List.copyOf(positions),
                        requestedLive
                            ? "OpenSky live positions — SE Asia"
                            : "DEMO positions — illustrative motion",
                        requestedLive,
                        System.currentTimeMillis() / 1000);
              } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                try {
                  snapshot =
                      new Snapshot(
                          List.copyOf(mock.fetch()),
                          "DEMO fallback — OpenSky unavailable; retry in 2 minutes",
                          false,
                          System.currentTimeMillis() / 1000);
                } catch (Exception fallback) {
                  snapshot =
                      new Snapshot(
                          List.of(),
                          "Tracking unavailable",
                          false,
                          System.currentTimeMillis() / 1000);
                }
              }
              return snapshot;
            },
            worker);
    return inFlight;
  }

  @Override
  public void close() {
    worker.shutdownNow();
  }
}
