package com.aircampus.event;

@FunctionalInterface
public interface Notifiable {
  void onNotification(AirportEvent event);
}
