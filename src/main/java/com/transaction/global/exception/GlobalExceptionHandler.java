package com.transaction.global.exception;

import com.transaction.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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

  @ExceptionHandler(StockCoreException.class)
  public ResponseEntity<ApiResponse<Void>> handleStockCoreException(
      StockCoreException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    return ResponseEntity.status(e.getStatus())
        .body(ApiResponse.fail(e.getCode(), e.getMessage(), traceId));
  }

  @ExceptionHandler(UserMappingNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleUserMappingNotFoundException(
      UserMappingNotFoundException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail("MAPPING_001", e.getMessage(), traceId));
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleUserNotFoundException(
      UserNotFoundException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.fail("USER_001", e.getMessage(), traceId));
  }
}
