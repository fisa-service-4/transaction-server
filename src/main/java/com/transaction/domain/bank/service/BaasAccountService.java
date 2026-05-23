package com.transaction.domain.bank.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.response.BaasAccountDetailResponse;
import com.transaction.domain.bank.dto.response.BaasAccountListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionCategoryListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionResponse;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasAccountService {

  private final BankCoreClient bankCoreClient;

  public ApiResponse<BaasAccountListResponse> getAccounts(
      Long userId, String traceId, String status) {
    log.info("[BaasAccountService] getAccounts 시작: userId={}, traceId={}", userId, traceId);

    ApiResponse<BaasAccountListResponse> response =
        bankCoreClient.getAccounts(userId, traceId, status);

    log.info(
        "[BaasAccountService] bank-server getAccounts 완료: count={}",
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasAccountDetailResponse> getAccount(
      Long userId, String traceId, Long accountId) {
    log.info(
        "[BaasAccountService] getAccount 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<BaasAccountDetailResponse> response =
        bankCoreClient.getAccount(userId, traceId, accountId);

    log.info("[BaasAccountService] bank-server getAccount 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactions(
      Long userId,
      String traceId,
      Long accountId,
      String fromDate,
      String toDate,
      Integer page,
      Integer size) {
    log.info(
        "[BaasAccountService] getTransactions 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        bankCoreClient.getTransactions(userId, traceId, accountId, fromDate, toDate, page, size);

    log.info(
        "[BaasAccountService] bank-server getTransactions 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactionsFilter(
      Long userId,
      String traceId,
      Long accountId,
      String type,
      String channel,
      String status,
      String fromDate,
      String toDate,
      String minAmount,
      String maxAmount,
      Integer page,
      Integer size) {
    log.info(
        "[BaasAccountService] getTransactionsFilter 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        bankCoreClient.getTransactionsFilter(
            userId, traceId, accountId, type, channel, status, fromDate, toDate, minAmount,
            maxAmount, page, size);

    log.info(
        "[BaasAccountService] bank-server getTransactionsFilter 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransactionCategoryListResponse> getTransactionCategories(
      Long userId, String traceId, Long accountId, String fromDate, String toDate) {
    log.info(
        "[BaasAccountService] getTransactionCategories 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<BaasTransactionCategoryListResponse> response =
        bankCoreClient.getTransactionCategories(userId, traceId, accountId, fromDate, toDate);

    log.info(
        "[BaasAccountService] bank-server getTransactionCategories 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }
}
