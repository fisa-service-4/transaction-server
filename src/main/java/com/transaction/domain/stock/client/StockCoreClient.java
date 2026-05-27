package com.transaction.domain.stock.client;

import com.transaction.domain.stock.dto.request.BaasStockOrderRequest;
import com.transaction.domain.stock.dto.response.BaasStockAccountListResponse;
import com.transaction.domain.stock.dto.response.BaasStockCashBalanceResponse;
import com.transaction.domain.stock.dto.response.BaasStockChartResponse;
import com.transaction.domain.stock.dto.response.BaasStockExecutionResponse;
import com.transaction.domain.stock.dto.response.BaasStockHoldingListResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderCancelResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderCreateResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderDetailResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderItemResponse;
import com.transaction.domain.stock.dto.response.BaasStockPriceResponse;
import com.transaction.domain.stock.dto.response.BaasStockReturnResponse;
import com.transaction.domain.stock.dto.response.BaasStockSearchResponse;
import com.transaction.global.config.StockFeignConfig;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "stock-core-client",
    url = "${core.stock.url}",
    configuration = StockFeignConfig.class)
public interface StockCoreClient {

  @GetMapping("/internal/v1/stock/search")
  ApiResponse<BaasStockSearchResponse> searchStocks(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestParam String keyword);

  @GetMapping("/internal/v1/stock/{stockCode}/price")
  ApiResponse<BaasStockPriceResponse> getStockPrice(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("stockCode") String stockCode);

  @GetMapping("/internal/v1/stock/{stockCode}/charts")
  ApiResponse<BaasStockChartResponse> getStockChart(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("stockCode") String stockCode,
      @RequestParam String interval,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate);

  @GetMapping("/internal/v1/stock/accounts")
  ApiResponse<BaasStockAccountListResponse> getStockAccounts(
      @RequestHeader("X-User-Id") Long userId, @RequestHeader("X-Trace-Id") String traceId);

  @GetMapping("/internal/v1/stock/accounts/{accountId}/cash-balance")
  ApiResponse<BaasStockCashBalanceResponse> getCashBalance(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId);

  @GetMapping("/internal/v1/stock/accounts/{accountId}/holdings")
  ApiResponse<BaasStockHoldingListResponse> getHoldings(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId);

  @GetMapping("/internal/v1/stock/accounts/{accountId}/executions")
  ApiResponse<PageResponse<BaasStockExecutionResponse>> getExecutions(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId,
      @RequestParam(required = false) String stockCode,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping("/internal/v1/stock/accounts/{accountId}/returns")
  ApiResponse<BaasStockReturnResponse> getReturns(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId);

  @PostMapping("/internal/v1/stock/accounts/{accountId}/orders")
  ApiResponse<BaasStockOrderCreateResponse> createOrder(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @PathVariable("accountId") Long accountId,
      @RequestBody BaasStockOrderRequest request);

  @PostMapping("/internal/v1/stock/orders/{orderId}/cancel")
  ApiResponse<BaasStockOrderCancelResponse> cancelOrder(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @PathVariable("orderId") Long orderId);

  @GetMapping("/internal/v1/stock/accounts/{accountId}/orders")
  ApiResponse<PageResponse<BaasStockOrderItemResponse>> getOrders(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String orderType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping("/internal/v1/stock/orders/{orderId}")
  ApiResponse<BaasStockOrderDetailResponse> getOrderDetail(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("orderId") Long orderId);
}
