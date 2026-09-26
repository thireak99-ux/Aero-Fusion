package com.aircampus.util;

import com.aircampus.exception.ValidationException;
import java.time.*;
import java.util.Locale;

public final class Checks {
  private Checks() {}

  public static void that(boolean condition, String message) {
    if (!condition) throw new ValidationException(message);
  }

  public static <T> T required(T value, String label) {
    that(value != null, "Choose " + label + ".");
    return value;
  }

  public static String text(String value, String label, int max) {
    that(value != null && !value.isBlank(), label + " is required.");
    String v = value.trim();
    that(v.length() <= max, label + " is too long (maximum " + max + ").");
    that(
        v.chars().noneMatch(c -> Character.isISOControl(c)),
        label + " contains unsupported characters.");
    return v;
  }

  public static String name(String value) {
    String v = text(value, "Full name", 100);
    that(v.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]{1,99}"), "Enter a valid full name.");
    return v;
  }

  public static String normalizedName(String value) {
    return name(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  public static String email(String value) {
    String v = text(value, "Email", 120).toLowerCase(Locale.ROOT);
    that(v.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Enter a valid email address.");
    return v;
  }

  public static String phone(String value) {
    String v = text(value, "Phone", 24).replaceAll("[ ()-]", "");
    that(
        v.matches("\\+?[0-9]{8,15}"),
        "Phone must contain 8–15 digits, optionally starting with +.");
    return v;
  }

  public static String username(String value) {
    String v = text(value, "Username", 30).toLowerCase(Locale.ROOT);
    that(v.matches("[a-z0-9_]{3,30}"), "Username needs 3–30 letters, digits or underscores.");
    return v;
  }

  public static String password(String value) {
    that(
        value != null
            && value.length() >= 8
            && value.length() <= 128
            && value.matches("(?s).*[0-9].*"),
        "Password needs 8–128 characters and at least one number.");
    return value;
  }

  public static String passport(String value) {
    String v = text(value, "Passport / ID number", 20).toUpperCase(Locale.ROOT);
    that(v.matches("[A-Z0-9]{6,20}"), "Passport / ID must contain 6–20 letters or digits.");
    return v;
  }

  public static void birthDate(LocalDate date, LocalDate today) {
    required(date, "date of birth");
    that(
        !date.isAfter(today) && !date.isBefore(today.minusYears(120)),
        "Enter a valid date of birth within the last 120 years.");
  }

  public static double decimal(String value, String label, double min, double max) {
    try {
      double n = Double.parseDouble(text(value, label, 24));
      range(n, label, min, max);
      return n;
    } catch (NumberFormatException e) {
      throw new ValidationException(label + " must be a number.");
    }
  }

  public static int integer(String value, String label, int min, int max) {
    try {
      int n = Integer.parseInt(text(value, label, 12));
      that(n >= min && n <= max, label + " must be between " + min + " and " + max + ".");
      return n;
    } catch (NumberFormatException e) {
      throw new ValidationException(label + " must be a whole number.");
    }
  }

  public static void range(double n, String label, double min, double max) {
    that(
        Double.isFinite(n) && n >= min && n <= max,
        label + " must be between " + min + " and " + max + ".");
  }

  public static void luhn(String input) {
    String value = text(input, "Card number", 32).replaceAll("[ -]", "");
    that(
        value.matches("[0-9]{13,19}") && !value.matches("0+"),
        "Card number must have 13–19 digits.");
    int sum = 0;
    boolean twice = false;
    for (int i = value.length() - 1; i >= 0; i--) {
      int n = value.charAt(i) - '0';
      if (twice) {
        n *= 2;
        if (n > 9) n -= 9;
      }
      sum += n;
      twice = !twice;
    }
    that(sum % 10 == 0, "Card number failed its Luhn checksum.");
  }

  public static void payment(
      String card, String expiry, String cvv, YearMonth current, boolean approve) {
    luhn(card);
    try {
      String[] a = text(expiry, "Expiry (MM/YY)", 5).split("/");
      that(a.length == 2 && a[0].length() == 2 && a[1].length() == 2, "Use MM/YY for expiry.");
      YearMonth exp = YearMonth.of(2000 + Integer.parseInt(a[1]), Integer.parseInt(a[0]));
      that(!exp.isBefore(current), "Card has expired.");
    } catch (DateTimeException | NumberFormatException e) {
      throw new ValidationException("Enter a valid expiry in MM/YY format.");
    }
    that(cvv != null && cvv.matches("[0-9]{3,4}"), "CVV must have 3 or 4 digits.");
    that(approve, "Simulated payment declined. No booking or charge was created.");
  }
}
