package com.aircampus.exception;

public class SecurityCheckException extends AppException {
  public SecurityCheckException(String message) {
    super(message);
  }

  public SecurityCheckException(String message, Throwable cause) {
    super(message, cause);
  }
}
