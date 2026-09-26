package com.aircampus.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formats {
  public static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH).withZone(ZONE);

  private Formats() {}

  public static String time(long seconds) {
    return TIME.format(Instant.ofEpochSecond(seconds));
  }

  public static String money(long cents) {
    return String.format(Locale.US, "$%,.2f", cents / 100.0);
  }

  public static LocalDate date(long seconds) {
    return Instant.ofEpochSecond(seconds).atZone(ZONE).toLocalDate();
  }

  public static long timestamp(LocalDate date, String time) {
    Checks.required(date, "date");
    try {
      return LocalDateTime.of(date, LocalTime.parse(Checks.text(time, "Time (HH:mm)", 5)))
          .atZone(ZONE)
          .toEpochSecond();
    } catch (java.time.format.DateTimeParseException e) {
      throw new com.aircampus.exception.ValidationException(
          "Enter time as HH:mm, for example 14:30.");
    }
  }
}
