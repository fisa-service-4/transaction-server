package com.transaction.domain.bank.controller;

import com.transaction.domain.bank.dto.response.BaasAccountDetailResponse;
import com.transaction.domain.bank.dto.response.BaasAccountListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionCategoryListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionResponse;
import com.transaction.domain.bank.service.BaasAccountService;
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
@RequestMapping("/baas/v1/bank/accounts")
@Tag(name = "BaaS Account", description = "은행 계좌 BaaS API")
public class BaasAccountController {

  private final BaasAccountService baasAccountService;

  @Operation(summary = "계좌 목록 조회")
  @GetMapping
  public ApiResponse<BaasAccountListResponse> getAccounts(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @RequestParam(required = false) String status,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts 요청: userId={}, traceId={}",
        userId,
        traceId);

    ApiResponse<BaasAccountListResponse> response =
        baasAccountService.getAccounts(userId, traceId, status);

    log.info("[BaasAccountController] GET /baas/v1/bank/accounts 완료: traceId={}", traceId);

    return response;
  }

  @Operation(summary = "계좌 상세 조회")
  @GetMapping("/{accountId}")
  public ApiResponse<BaasAccountDetailResponse> getAccount(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{} 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<BaasAccountDetailResponse> response =
        baasAccountService.getAccount(userId, traceId, accountId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{} 완료: traceId={}", accountId, traceId);

    return response;
  }

  @Operation(summary = "거래내역 조회")
  @GetMapping("/{accountId}/transactions")
  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactions(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        baasAccountService.getTransactions(
            userId, traceId, accountId, fromDate, toDate, page, size);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "거래내역 필터 조회")
  @GetMapping("/{accountId}/transactions/filter")
  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactionsFilter(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) String minAmount,
      @RequestParam(required = false) String maxAmount,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions/filter 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        baasAccountService.getTransactionsFilter(
            userId, traceId, accountId, type, channel, status, fromDate, toDate, minAmount,
            maxAmount, page, size);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions/filter 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "거래 카테고리 집계 조회")
  @GetMapping("/{accountId}/transactions/categories")
  public ApiResponse<BaasTransactionCategoryListResponse> getTransactionCategories(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      @RequestParam String fromDate,
      @RequestParam String toDate,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions/categories 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<BaasTransactionCategoryListResponse> response =
        baasAccountService.getTransactionCategories(userId, traceId, accountId, fromDate, toDate);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions/categories 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  private String resolveTraceId(String xTraceId) {
    return (xTraceId != null && !xTraceId.isBlank()) ? xTraceId : UUID.randomUUID().toString();
  }
}
