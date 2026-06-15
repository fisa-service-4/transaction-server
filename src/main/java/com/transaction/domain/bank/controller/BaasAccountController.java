package com.transaction.domain.bank.controller;

import com.transaction.domain.bank.dto.response.BaasAccountBalanceResponse;
import com.transaction.domain.bank.dto.response.BaasAccountDetailResponse;
import com.transaction.domain.bank.dto.response.BaasAccountListResponse;
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
      @RequestParam(required = false) String status,
      @RequestHeader("X-Firebase-Uid") String firebaseUid,
      HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info("[BaasAccountController] GET /baas/v1/bank/accounts 요청: traceId={}", traceId);

    ApiResponse<BaasAccountListResponse> response =
        baasAccountService.getAccounts(traceId, status, firebaseUid);

    log.info("[BaasAccountController] GET /baas/v1/bank/accounts 완료: traceId={}", traceId);

    return response;
  }

  @Operation(summary = "계좌 상세 조회")
  @GetMapping("/{accountId}")
  public ApiResponse<BaasAccountDetailResponse> getAccount(
      @PathVariable Long accountId, HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{} 요청: traceId={}", accountId, traceId);

    ApiResponse<BaasAccountDetailResponse> response =
        baasAccountService.getAccount(traceId, accountId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{} 완료: traceId={}", accountId, traceId);

    return response;
  }

  @Operation(summary = "계좌 잔액 조회")
  @GetMapping("/{accountId}/balance")
  public ApiResponse<BaasAccountBalanceResponse> getAccountBalance(
      @PathVariable Long accountId, HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/balance 요청: traceId={}",
        accountId,
        traceId);

    ApiResponse<BaasAccountBalanceResponse> response =
        baasAccountService.getAccountBalance(traceId, accountId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/balance 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "거래내역 조회")
  @GetMapping("/{accountId}/transactions")
  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactions(
      @PathVariable Long accountId,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions 요청: traceId={}",
        accountId,
        traceId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        baasAccountService.getTransactions(traceId, accountId, fromDate, toDate, page, size);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "거래내역 필터 조회")
  @GetMapping("/{accountId}/transactions/filter")
  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactionsFilter(
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
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions/filter 요청: traceId={}",
        accountId,
        traceId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        baasAccountService.getTransactionsFilter(
            traceId, accountId, type, channel, status, fromDate, toDate, minAmount, maxAmount, page,
            size);

    log.info(
        "[BaasAccountController] GET /baas/v1/bank/accounts/{}/transactions/filter 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString();
  }
}
