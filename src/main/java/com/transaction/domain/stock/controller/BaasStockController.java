package com.transaction.domain.stock.controller;

import com.transaction.domain.stock.dto.response.BaasStockChartResponse;
import com.transaction.domain.stock.dto.response.BaasStockPriceResponse;
import com.transaction.domain.stock.dto.response.BaasStockSearchResponse;
import com.transaction.domain.stock.service.BaasStockService;
import com.transaction.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/baas/v1/stock")
@Tag(name = "BaaS Stock", description = "주식 시세/종목 BaaS API")
public class BaasStockController {

  private final BaasStockService baasStockService;

  @Operation(summary = "종목 검색")
  @GetMapping("/search")
  public ApiResponse<BaasStockSearchResponse> searchStocks(
      @RequestParam String keyword, HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info("[BaasStockController] GET /baas/v1/stock/search 요청: traceId={}", traceId);

    ApiResponse<BaasStockSearchResponse> response =
        baasStockService.searchStocks(traceId, keyword);

    log.info("[BaasStockController] GET /baas/v1/stock/search 완료: traceId={}", traceId);

    return response;
  }

  @Operation(summary = "현재가 조회")
  @GetMapping("/{stockCode}/price")
  public ApiResponse<BaasStockPriceResponse> getStockPrice(
      @PathVariable String stockCode, HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockController] GET /baas/v1/stock/{}/price 요청: traceId={}", stockCode, traceId);

    ApiResponse<BaasStockPriceResponse> response =
        baasStockService.getStockPrice(traceId, stockCode);

    log.info(
        "[BaasStockController] GET /baas/v1/stock/{}/price 완료: traceId={}", stockCode, traceId);

    return response;
  }

  @Operation(summary = "차트 조회")
  @GetMapping("/{stockCode}/charts")
  public ApiResponse<BaasStockChartResponse> getStockChart(
      @PathVariable String stockCode,
      @RequestParam String interval,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockController] GET /baas/v1/stock/{}/charts 요청: traceId={}", stockCode, traceId);

    ApiResponse<BaasStockChartResponse> response =
        baasStockService.getStockChart(traceId, stockCode, interval, fromDate, toDate);

    log.info(
        "[BaasStockController] GET /baas/v1/stock/{}/charts 완료: traceId={}", stockCode, traceId);

    return response;
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString();
  }
}
