package com.aircampus.exception;

public class InvalidBookingException extends AppException {
  public InvalidBookingException(String message) {
    super(message);
  }

  public InvalidBookingException(String message, Throwable cause) {
    super(message, cause);
  }
}
