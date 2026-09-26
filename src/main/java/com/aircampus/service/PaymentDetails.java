package com.aircampus.service;

import com.aircampus.util.Formats;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Ephemeral input only. Card number and CVV are never saved or logged. */
public record PaymentDetails(String cardNumber, String expiry, String cvv, boolean approve) {
  public static PaymentDetails demo(Clock clock) {
    int year = clock.instant().atZone(Formats.ZONE).getYear();
    String expiry =
        YearMonth.of(Math.min(2099, year + 3), 12).format(DateTimeFormatter.ofPattern("MM/yy"));
    return new PaymentDetails("4111111111111111", expiry, "123", true);
  }
}
