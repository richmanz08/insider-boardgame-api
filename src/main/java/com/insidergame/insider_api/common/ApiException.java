package com.insidergame.insider_api.common;

import org.springframework.http.HttpStatus;

/**
 * Custom runtime exception that carries a message and an HTTP status.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;

  public ApiException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
