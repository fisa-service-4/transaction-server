package com.transaction.global.exception;

import com.transaction.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BankCoreException.class)
  public ResponseEntity<ApiResponse<Void>> handleBankCoreException(
      BankCoreException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    log.error(
        "[GlobalExceptionHandler] BankCoreException: uri={}, code={}, message={}",
        request.getRequestURI(),
        e.getCode(),
        e.getMessage());
    return ResponseEntity.status(e.getStatus())
        .body(ApiResponse.fail(e.getCode(), e.getMessage(), traceId));
  }

  @ExceptionHandler(StockCoreException.class)
  public ResponseEntity<ApiResponse<Void>> handleStockCoreException(
      StockCoreException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    log.error(
        "[GlobalExceptionHandler] StockCoreException: uri={}, code={}, message={}",
        request.getRequestURI(),
        e.getCode(),
        e.getMessage());
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

  @ExceptionHandler(SagaException.class)
  public ResponseEntity<ApiResponse<Void>> handleSagaException(
      SagaException e, HttpServletRequest request) {
    String traceId = (String) request.getAttribute("traceId");
    log.error(
        "[GlobalExceptionHandler] SagaException: uri={}, code={}, message={}",
        request.getRequestURI(),
        e.getCode(),
        e.getMessage());
    return ResponseEntity.status(e.getStatus())
        .body(ApiResponse.fail(e.getCode(), e.getMessage(), traceId));
  }
}
