package com.transaction.domain.stock.service;

import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.response.BaasStockChartResponse;
import com.transaction.domain.stock.dto.response.BaasStockPriceResponse;
import com.transaction.domain.stock.dto.response.BaasStockSearchResponse;
import com.transaction.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasStockService {

  private final StockCoreClient stockCoreClient;

  public ApiResponse<BaasStockSearchResponse> searchStocks(
      Long userId, String traceId, String keyword) {
    log.info("[BaasStockService] searchStocks 시작: userId={}, traceId={}", userId, traceId);

    ApiResponse<BaasStockSearchResponse> response =
        stockCoreClient.searchStocks(userId, traceId, keyword);

    log.info(
        "[BaasStockService] stock-server searchStocks 완료: count={}",
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockPriceResponse> getStockPrice(
      Long userId, String traceId, String stockCode) {
    log.info(
        "[BaasStockService] getStockPrice 시작: userId={}, traceId={}, stockCode={}",
        userId,
        traceId,
        stockCode);

    ApiResponse<BaasStockPriceResponse> response =
        stockCoreClient.getStockPrice(userId, traceId, stockCode);

    log.info("[BaasStockService] stock-server getStockPrice 완료: stockCode={}", stockCode);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockChartResponse> getStockChart(
      Long userId,
      String traceId,
      String stockCode,
      String interval,
      String fromDate,
      String toDate) {
    log.info(
        "[BaasStockService] getStockChart 시작: userId={}, traceId={}, stockCode={}",
        userId,
        traceId,
        stockCode);

    ApiResponse<BaasStockChartResponse> response =
        stockCoreClient.getStockChart(userId, traceId, stockCode, interval, fromDate, toDate);

    log.info("[BaasStockService] stock-server getStockChart 완료: stockCode={}", stockCode);

    return ApiResponse.success(response.getData(), traceId);
  }
}
