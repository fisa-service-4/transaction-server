package com.transaction.domain.stock.service;

import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.request.BaasStockOrderRequest;
import com.transaction.domain.stock.dto.response.BaasStockOrderCancelResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderCreateResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderDetailResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderItemResponse;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasStockOrderService {

  private final StockCoreClient stockCoreClient;

  public ApiResponse<BaasStockOrderCreateResponse> createOrder(
      Long userId,
      String traceId,
      String idempotencyKey,
      Long accountId,
      BaasStockOrderRequest request) {
    log.info(
        "[BaasStockOrderService] createOrder 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<BaasStockOrderCreateResponse> response =
        stockCoreClient.createOrder(userId, traceId, idempotencyKey, accountId, request);

    log.info(
        "[BaasStockOrderService] stock-server createOrder 완료: orderId={}",
        response.getData().getOrderId());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockOrderCancelResponse> cancelOrder(
      Long userId, String traceId, String idempotencyKey, Long orderId) {
    log.info(
        "[BaasStockOrderService] cancelOrder 시작: userId={}, traceId={}, orderId={}",
        userId,
        traceId,
        orderId);

    ApiResponse<BaasStockOrderCancelResponse> response =
        stockCoreClient.cancelOrder(userId, traceId, idempotencyKey, orderId);

    log.info("[BaasStockOrderService] stock-server cancelOrder 완료: orderId={}", orderId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasStockOrderItemResponse>> getOrders(
      Long userId,
      String traceId,
      Long accountId,
      String status,
      String orderType,
      Integer page,
      Integer size) {
    log.info(
        "[BaasStockOrderService] getOrders 시작: userId={}, traceId={}, accountId={}",
        userId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasStockOrderItemResponse>> response =
        stockCoreClient.getOrders(userId, traceId, accountId, status, orderType, page, size);

    log.info(
        "[BaasStockOrderService] stock-server getOrders 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockOrderDetailResponse> getOrderDetail(
      Long userId, String traceId, Long orderId) {
    log.info(
        "[BaasStockOrderService] getOrderDetail 시작: userId={}, traceId={}, orderId={}",
        userId,
        traceId,
        orderId);

    ApiResponse<BaasStockOrderDetailResponse> response =
        stockCoreClient.getOrderDetail(userId, traceId, orderId);

    log.info("[BaasStockOrderService] stock-server getOrderDetail 완료: orderId={}", orderId);

    return ApiResponse.success(response.getData(), traceId);
  }
}
