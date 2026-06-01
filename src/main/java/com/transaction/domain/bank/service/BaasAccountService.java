package com.transaction.domain.bank.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.response.BaasAccountBalanceResponse;
import com.transaction.domain.bank.dto.response.BaasAccountDetailResponse;
import com.transaction.domain.bank.dto.response.BaasAccountListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionCategoryListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionCategoryResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionResponse;
import com.transaction.global.exception.BankCoreException;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasAccountService {

  private final BankCoreClient bankCoreClient;
  private final UserResolver userResolver;

  public ApiResponse<BaasAccountListResponse> getAccounts(
      String traceId, String status, String firebaseUid) {
    Long xUserId = userResolver.resolveByFirebaseUid(firebaseUid);
    log.info("[BaasAccountService] getAccounts 시작: xUserId={}, traceId={}", xUserId, traceId);

    try {
      ApiResponse<BaasAccountListResponse> response =
          bankCoreClient.getAccounts(xUserId, traceId, status);

      log.info(
          "[BaasAccountService] bank-server getAccounts 완료: count={}",
          response.getData().getContent() != null ? response.getData().getContent().size() : 0);

      return ApiResponse.success(response.getData(), traceId);
    } catch (BankCoreException e) {
      log.info("[BaasAccountService] bank-server 계좌 없음: xUserId={}, code={}", xUserId, e.getCode());
      return ApiResponse.success(new BaasAccountListResponse(), traceId);
    }
  }

  public ApiResponse<BaasAccountDetailResponse> getAccount(String traceId, Long accountId) {
    Long xUserId = userResolver.resolveByAccount(accountId, "BANK");
    log.info(
        "[BaasAccountService] getAccount 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasAccountDetailResponse> response =
        bankCoreClient.getAccount(xUserId, traceId, accountId);

    log.info("[BaasAccountService] bank-server getAccount 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasAccountBalanceResponse> getAccountBalance(String traceId, Long accountId) {
    Long xUserId = userResolver.resolveByAccount(accountId, "BANK");
    log.info(
        "[BaasAccountService] getAccountBalance 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasAccountBalanceResponse> response =
        bankCoreClient.getAccountBalance(xUserId, traceId, accountId);

    log.info("[BaasAccountService] bank-server getAccountBalance 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactions(
      String traceId, Long accountId, String fromDate, String toDate, Integer page, Integer size) {
    Long xUserId = userResolver.resolveByAccount(accountId, "BANK");
    log.info(
        "[BaasAccountService] getTransactions 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        bankCoreClient.getTransactions(
            xUserId, traceId, accountId, null, null, null, fromDate, toDate, null, null, page,
            size);

    log.info(
        "[BaasAccountService] bank-server getTransactions 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasTransactionResponse>> getTransactionsFilter(
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
    Long xUserId = userResolver.resolveByAccount(accountId, "BANK");
    log.info(
        "[BaasAccountService] getTransactionsFilter 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasTransactionResponse>> response =
        bankCoreClient.getTransactions(
            xUserId, traceId, accountId, type, channel, status, fromDate, toDate, minAmount,
            maxAmount, page, size);

    log.info(
        "[BaasAccountService] bank-server getTransactionsFilter 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransactionCategoryListResponse> getTransactionCategories(
      String traceId, Long accountId, String fromDate, String toDate) {
    Long xUserId = userResolver.resolveByAccount(accountId, "BANK");
    log.info(
        "[BaasAccountService] getTransactionCategories 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<List<BaasTransactionCategoryResponse>> response =
        bankCoreClient.getTransactionCategories(xUserId, traceId, accountId, fromDate, toDate);

    log.info(
        "[BaasAccountService] bank-server getTransactionCategories 완료: accountId={}", accountId);

    return ApiResponse.success(
        new BaasTransactionCategoryListResponse(response.getData()), traceId);
  }
}
