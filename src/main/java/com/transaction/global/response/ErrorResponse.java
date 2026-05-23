package com.transaction.global.response;

import lombok.Getter;

@Getter
public class ErrorResponse {

  private final String code;
  private final String message;

  private ErrorResponse(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse(code, message);
  }
}
