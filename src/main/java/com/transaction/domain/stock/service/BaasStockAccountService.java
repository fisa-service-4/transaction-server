package com.transaction.domain.stock.service;

import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.response.BaasStockAccountListResponse;
import com.transaction.domain.stock.dto.response.BaasStockCashBalanceResponse;
import com.transaction.domain.stock.dto.response.BaasStockExecutionResponse;
import com.transaction.domain.stock.dto.response.BaasStockHoldingListResponse;
import com.transaction.domain.stock.dto.response.BaasStockReturnResponse;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasStockAccountService {

  private final StockCoreClient stockCoreClient;

  public ApiResponse<BaasStockAccountListResponse> getStockAccounts(Long userId, String traceId) {
    log.info(
        "[BaasStockAccountService] getStockAccounts 시작: userId={}, traceId={}", userId, traceId);

    ApiResponse<BaasStockAccountListResponse> response =
        stockCoreClient.getStockAccounts(userId, traceId);

    log.info(
        "[BaasStockAccountService] stock-server getStockAccounts 완료: count={}",
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockCashBalanceResponse> getCashBalance(
      Long userId, String traceId, Long accountId) {
    log.info(
        "[BaasStockAccountService] getCashBalance 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<BaasStockCashBalanceResponse> response =
        stockCoreClient.getCashBalance(userId, traceId, accountId);

    log.info("[BaasStockAccountService] stock-server getCashBalance 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockHoldingListResponse> getHoldings(
      Long userId, String traceId, Long accountId) {
    log.info(
        "[BaasStockAccountService] getHoldings 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<BaasStockHoldingListResponse> response =
        stockCoreClient.getHoldings(userId, traceId, accountId);

    log.info(
        "[BaasStockAccountService] stock-server getHoldings 완료: accountId={}, count={}",
        accountId,
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasStockExecutionResponse>> getExecutions(
      Long userId,
      String traceId,
      Long accountId,
      String stockCode,
      String fromDate,
      String toDate,
      Integer page,
      Integer size) {
    log.info(
        "[BaasStockAccountService] getExecutions 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasStockExecutionResponse>> response =
        stockCoreClient.getExecutions(
            userId, traceId, accountId, stockCode, fromDate, toDate, page, size);

    log.info(
        "[BaasStockAccountService] stock-server getExecutions 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockReturnResponse> getReturns(
      Long userId, String traceId, Long accountId) {
    log.info(
        "[BaasStockAccountService] getReturns 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<BaasStockReturnResponse> response =
        stockCoreClient.getReturns(userId, traceId, accountId);

    log.info("[BaasStockAccountService] stock-server getReturns 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }
}
