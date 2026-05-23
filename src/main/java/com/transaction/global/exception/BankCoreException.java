package com.transaction.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BankCoreException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  public BankCoreException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
