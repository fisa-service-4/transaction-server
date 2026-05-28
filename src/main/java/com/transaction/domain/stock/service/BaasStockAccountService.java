package com.transaction.domain.stock.service;

import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.response.BaasStockAccountListResponse;
import com.transaction.domain.stock.dto.response.BaasStockCashBalanceResponse;
import com.transaction.domain.stock.dto.response.BaasStockExecutionResponse;
import com.transaction.domain.stock.dto.response.BaasStockHoldingListResponse;
import com.transaction.domain.stock.dto.response.BaasStockReturnResponse;
import com.transaction.global.resolver.UserResolver;
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
  private final UserResolver userResolver;

  public ApiResponse<BaasStockAccountListResponse> getStockAccounts(
      String traceId, String firebaseUid) {
    Long xUserId = userResolver.resolveByFirebaseUid(firebaseUid);
    log.info(
        "[BaasStockAccountService] getStockAccounts 시작: xUserId={}, traceId={}", xUserId, traceId);

    ApiResponse<BaasStockAccountListResponse> response =
        stockCoreClient.getStockAccounts(xUserId, traceId);

    log.info(
        "[BaasStockAccountService] stock-server getStockAccounts 완료: count={}",
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockCashBalanceResponse> getCashBalance(
      String traceId, Long accountId) {
    Long xUserId = userResolver.resolveByAccount(accountId, "STOCK");
    log.info(
        "[BaasStockAccountService] getCashBalance 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasStockCashBalanceResponse> response =
        stockCoreClient.getCashBalance(xUserId, traceId, accountId);

    log.info("[BaasStockAccountService] stock-server getCashBalance 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockHoldingListResponse> getHoldings(String traceId, Long accountId) {
    Long xUserId = userResolver.resolveByAccount(accountId, "STOCK");
    log.info(
        "[BaasStockAccountService] getHoldings 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasStockHoldingListResponse> response =
        stockCoreClient.getHoldings(xUserId, traceId, accountId);

    log.info(
        "[BaasStockAccountService] stock-server getHoldings 완료: accountId={}, count={}",
        accountId,
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasStockExecutionResponse>> getExecutions(
      String traceId,
      Long accountId,
      String stockCode,
      String fromDate,
      String toDate,
      Integer page,
      Integer size) {
    Long xUserId = userResolver.resolveByAccount(accountId, "STOCK");
    log.info(
        "[BaasStockAccountService] getExecutions 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasStockExecutionResponse>> response =
        stockCoreClient.getExecutions(
            xUserId, traceId, accountId, stockCode, fromDate, toDate, page, size);

    log.info(
        "[BaasStockAccountService] stock-server getExecutions 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockReturnResponse> getReturns(String traceId, Long accountId) {
    Long xUserId = userResolver.resolveByAccount(accountId, "STOCK");
    log.info(
        "[BaasStockAccountService] getReturns 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasStockReturnResponse> response =
        stockCoreClient.getReturns(xUserId, traceId, accountId);

    log.info("[BaasStockAccountService] stock-server getReturns 완료: accountId={}", accountId);

    return ApiResponse.success(response.getData(), traceId);
  }
}
