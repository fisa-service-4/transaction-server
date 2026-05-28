package com.transaction.domain.stock.service;

import com.transaction.domain.mapping.entity.OrderUserMapping;
import com.transaction.domain.mapping.repository.OrderUserMappingRepository;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.request.BaasStockOrderRequest;
import com.transaction.domain.stock.dto.response.BaasStockOrderCancelResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderCreateResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderDetailResponse;
import com.transaction.domain.stock.dto.response.BaasStockOrderItemResponse;
import com.transaction.global.resolver.UserResolver;
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
  private final UserResolver userResolver;
  private final OrderUserMappingRepository orderUserMappingRepository;

  public ApiResponse<BaasStockOrderCreateResponse> createOrder(
      String traceId, String idempotencyKey, Long accountId, BaasStockOrderRequest request) {
    Long xUserId = userResolver.resolveByAccount(accountId, "STOCK");
    log.info(
        "[BaasStockOrderService] createOrder 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasStockOrderCreateResponse> response =
        stockCoreClient.createOrder(xUserId, traceId, idempotencyKey, accountId, request);

    Long orderId = response.getData().getOrderId();
    orderUserMappingRepository.save(new OrderUserMapping(orderId, xUserId));

    log.info("[BaasStockOrderService] stock-server createOrder 완료: orderId={}", orderId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockOrderCancelResponse> cancelOrder(
      String traceId, String idempotencyKey, Long orderId) {
    Long xUserId = userResolver.resolveByOrderId(orderId);
    log.info(
        "[BaasStockOrderService] cancelOrder 시작: xUserId={}, traceId={}, orderId={}",
        xUserId,
        traceId,
        orderId);

    ApiResponse<BaasStockOrderCancelResponse> response =
        stockCoreClient.cancelOrder(xUserId, traceId, idempotencyKey, orderId);

    log.info("[BaasStockOrderService] stock-server cancelOrder 완료: orderId={}", orderId);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasStockOrderItemResponse>> getOrders(
      String traceId, Long accountId, String status, String orderType, Integer page, Integer size) {
    Long xUserId = userResolver.resolveByAccount(accountId, "STOCK");
    log.info(
        "[BaasStockOrderService] getOrders 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<PageResponse<BaasStockOrderItemResponse>> response =
        stockCoreClient.getOrders(xUserId, traceId, accountId, status, orderType, page, size);

    log.info(
        "[BaasStockOrderService] stock-server getOrders 완료: accountId={}, totalElements={}",
        accountId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasStockOrderDetailResponse> getOrderDetail(String traceId, Long orderId) {
    Long xUserId = userResolver.resolveByOrderId(orderId);
    log.info(
        "[BaasStockOrderService] getOrderDetail 시작: xUserId={}, traceId={}, orderId={}",
        xUserId,
        traceId,
        orderId);

    ApiResponse<BaasStockOrderDetailResponse> response =
        stockCoreClient.getOrderDetail(xUserId, traceId, orderId);

    log.info("[BaasStockOrderService] stock-server getOrderDetail 완료: orderId={}", orderId);

    return ApiResponse.success(response.getData(), traceId);
  }
}
