package com.transaction.domain.stock.service;

import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.response.BaasStockChartResponse;
import com.transaction.domain.stock.dto.response.BaasStockPriceResponse;
import com.transaction.domain.stock.dto.response.BaasStockSearchResponse;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasStockService {

  private final StockCoreClient stockCoreClient;
  private final UserResolver userResolver;

  public ApiResponse<BaasStockSearchResponse> searchStocks(String traceId, String keyword) {
    Long xUserId = userResolver.systemUserId();
    log.info("[BaasStockService] searchStocks 시작: xUserId={}, traceId={}", xUserId, traceId);

    ApiResponse<BaasStockSearchResponse> response =
        stockCoreClient.searchStocks(xUserId, traceId, keyword);

    log.info(
        "[BaasStockService] stock-server searchStocks 완료: count={}",
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockPriceResponse> getStockPrice(String traceId, String stockCode) {
    Long xUserId = userResolver.systemUserId();
    log.info(
        "[BaasStockService] getStockPrice 시작: xUserId={}, traceId={}, stockCode={}",
        xUserId,
        traceId,
        stockCode);

    ApiResponse<BaasStockPriceResponse> response =
        stockCoreClient.getStockPrice(xUserId, traceId, stockCode);

    log.info("[BaasStockService] stock-server getStockPrice 완료: stockCode={}", stockCode);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockChartResponse> getStockChart(
      String traceId, String stockCode, String interval, String fromDate, String toDate) {
    Long xUserId = userResolver.systemUserId();
    log.info(
        "[BaasStockService] getStockChart 시작: xUserId={}, traceId={}, stockCode={}",
        xUserId,
        traceId,
        stockCode);

    ApiResponse<BaasStockChartResponse> response =
        stockCoreClient.getStockChart(xUserId, traceId, stockCode, interval, fromDate, toDate);

    log.info("[BaasStockService] stock-server getStockChart 완료: stockCode={}", stockCode);

    return ApiResponse.success(response.getData(), traceId);
  }
}
