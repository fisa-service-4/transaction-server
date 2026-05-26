package com.transaction.domain.stock.controller;

import com.transaction.domain.stock.dto.response.BaasStockAccountListResponse;
import com.transaction.domain.stock.dto.response.BaasStockCashBalanceResponse;
import com.transaction.domain.stock.dto.response.BaasStockExecutionResponse;
import com.transaction.domain.stock.dto.response.BaasStockHoldingListResponse;
import com.transaction.domain.stock.dto.response.BaasStockReturnResponse;
import com.transaction.domain.stock.service.BaasStockAccountService;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/baas/v1/stock/accounts")
@Tag(name = "BaaS Stock Account", description = "주식 계좌 BaaS API")
public class BaasStockAccountController {

  private final BaasStockAccountService baasStockAccountService;

  @Operation(summary = "주문 가능 계좌 조회")
  @GetMapping
  public ApiResponse<BaasStockAccountListResponse> getStockAccounts(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts 요청: userId={}, traceId={}",
        userId,
        traceId);

    ApiResponse<BaasStockAccountListResponse> response =
        baasStockAccountService.getStockAccounts(userId, traceId);

    log.info("[BaasStockAccountController] GET /baas/v1/stock/accounts 완료: traceId={}", traceId);

    return response;
  }

  @Operation(summary = "예수금 조회")
  @GetMapping("/{accountId}/cash-balance")
  public ApiResponse<BaasStockCashBalanceResponse> getCashBalance(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/cash-balance 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<BaasStockCashBalanceResponse> response =
        baasStockAccountService.getCashBalance(userId, traceId, accountId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/cash-balance 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "보유 종목 조회")
  @GetMapping("/{accountId}/holdings")
  public ApiResponse<BaasStockHoldingListResponse> getHoldings(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/holdings 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<BaasStockHoldingListResponse> response =
        baasStockAccountService.getHoldings(userId, traceId, accountId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/holdings 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "체결 내역 조회")
  @GetMapping("/{accountId}/executions")
  public ApiResponse<PageResponse<BaasStockExecutionResponse>> getExecutions(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      @RequestParam(required = false) String stockCode,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/executions 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<PageResponse<BaasStockExecutionResponse>> response =
        baasStockAccountService.getExecutions(
            userId, traceId, accountId, stockCode, fromDate, toDate, page, size);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/executions 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "수익률 조회")
  @GetMapping("/{accountId}/returns")
  public ApiResponse<BaasStockReturnResponse> getReturns(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/returns 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<BaasStockReturnResponse> response =
        baasStockAccountService.getReturns(userId, traceId, accountId);

    log.info(
        "[BaasStockAccountController] GET /baas/v1/stock/accounts/{}/returns 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  private String resolveTraceId(String xTraceId) {
    return (xTraceId != null && !xTraceId.isBlank()) ? xTraceId : UUID.randomUUID().toString();
  }
}
