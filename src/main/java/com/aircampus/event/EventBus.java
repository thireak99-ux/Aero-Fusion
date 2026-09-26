package com.aircampus.event;

import java.util.concurrent.CopyOnWriteArrayList;

public final class EventBus {
  private final CopyOnWriteArrayList<Notifiable> observers = new CopyOnWriteArrayList<>();

  public AutoCloseable subscribe(Notifiable observer) {
    observers.add(observer);
    return () -> observers.remove(observer);
  }

  public void publish(AirportEvent event) {
    for (Notifiable observer : observers) {
      try {
        observer.onNotification(event);
      } catch (RuntimeException e) {
        System.err.println("A view could not refresh: " + e.getMessage());
      }
    }
  }
}
