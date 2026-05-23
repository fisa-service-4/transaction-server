package com.transaction.global.exception;

import com.transaction.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BankCoreException.class)
  public ResponseEntity<ApiResponse<Void>> handleBankCoreException(
      BankCoreException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    return ResponseEntity.status(e.getStatus())
        .body(ApiResponse.fail(e.getCode(), e.getMessage(), traceId));
  }
}
