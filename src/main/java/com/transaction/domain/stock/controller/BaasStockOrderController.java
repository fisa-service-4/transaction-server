package com.transaction.domain.stock.controller;

import com.transaction.domain.stock.dto.request.BaasStockOrderRequest;
import com.transaction.domain.stock.dto.response.BaasStockOrderCancelResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderCreateResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderDetailResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderItemResponse;
import com.transaction.domain.stock.service.BaasStockOrderService;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/baas/v1/stock")
@Tag(name = "BaaS Stock Order", description = "주식 주문 BaaS API")
public class BaasStockOrderController {

  private final BaasStockOrderService baasStockOrderService;

  @Operation(summary = "주문 생성")
  @PostMapping("/accounts/{accountId}/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<BaasStockOrderCreateResponse> createOrder(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      @Valid @RequestBody BaasStockOrderRequest request,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockOrderController] POST /baas/v1/stock/accounts/{}/orders 요청: userId={}, traceId={}, idempotencyKey={}",
        accountId,
        userId,
        traceId,
        idempotencyKey);

    ApiResponse<BaasStockOrderCreateResponse> response =
        baasStockOrderService.createOrder(userId, traceId, idempotencyKey, accountId, request);

    log.info(
        "[BaasStockOrderController] POST /baas/v1/stock/accounts/{}/orders 완료: orderId={}, traceId={}",
        accountId,
        response.getData().getOrderId(),
        traceId);

    return response;
  }

  @Operation(summary = "주문 취소")
  @PostMapping("/orders/{orderId}/cancel")
  public ApiResponse<BaasStockOrderCancelResponse> cancelOrder(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long orderId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockOrderController] POST /baas/v1/stock/orders/{}/cancel 요청: userId={}, traceId={}, idempotencyKey={}",
        orderId,
        userId,
        traceId,
        idempotencyKey);

    ApiResponse<BaasStockOrderCancelResponse> response =
        baasStockOrderService.cancelOrder(userId, traceId, idempotencyKey, orderId);

    log.info(
        "[BaasStockOrderController] POST /baas/v1/stock/orders/{}/cancel 완료: traceId={}",
        orderId,
        traceId);

    return response;
  }

  @Operation(summary = "주문 목록 조회")
  @GetMapping("/accounts/{accountId}/orders")
  public ApiResponse<PageResponse<BaasStockOrderItemResponse>> getOrders(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long accountId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String orderType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockOrderController] GET /baas/v1/stock/accounts/{}/orders 요청: userId={}, traceId={}",
        accountId,
        userId,
        traceId);

    ApiResponse<PageResponse<BaasStockOrderItemResponse>> response =
        baasStockOrderService.getOrders(userId, traceId, accountId, status, orderType, page, size);

    log.info(
        "[BaasStockOrderController] GET /baas/v1/stock/accounts/{}/orders 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "주문 상세 조회")
  @GetMapping("/orders/{orderId}")
  public ApiResponse<BaasStockOrderDetailResponse> getOrderDetail(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader(value = "X-Trace-Id", required = false) String xTraceId,
      @PathVariable Long orderId,
      HttpServletRequest httpRequest) {
    String traceId = resolveTraceId(xTraceId);
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasStockOrderController] GET /baas/v1/stock/orders/{} 요청: userId={}, traceId={}",
        orderId,
        userId,
        traceId);

    ApiResponse<BaasStockOrderDetailResponse> response =
        baasStockOrderService.getOrderDetail(userId, traceId, orderId);

    log.info(
        "[BaasStockOrderController] GET /baas/v1/stock/orders/{} 완료: traceId={}", orderId, traceId);

    return response;
  }

  private String resolveTraceId(String xTraceId) {
    return (xTraceId != null && !xTraceId.isBlank()) ? xTraceId : UUID.randomUUID().toString();
  }
}
