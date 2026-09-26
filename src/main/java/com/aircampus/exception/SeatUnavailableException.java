package com.aircampus.exception;

public class SeatUnavailableException extends AppException {
  public SeatUnavailableException(String message) {
    super(message);
  }

  public SeatUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
