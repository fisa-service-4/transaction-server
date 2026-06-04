package com.transaction.global.exception;

import org.springframework.http.HttpStatus;

public class SagaException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  public SagaException(String message) {
    super(message);
    this.code = "SAGA_001";
    this.status = HttpStatus.INTERNAL_SERVER_ERROR;
  }

  public SagaException(String message, Throwable cause) {
    super(message, cause);
    this.code = "SAGA_001";
    this.status = HttpStatus.INTERNAL_SERVER_ERROR;
  }

  public SagaException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
