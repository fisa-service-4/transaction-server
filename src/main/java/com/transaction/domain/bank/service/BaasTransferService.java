package com.transaction.domain.bank.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasTransferService {

  private final BankCoreClient bankCoreClient;

  public ApiResponse<BaasTransferCreateResponse> createTransfer(
      Long userId, String traceId, String idempotencyKey, BaasTransferRequest request) {
    log.info(
        "[BaasTransferService] createTransfer 시작: userId={}, traceId={}, idempotencyKey={}",
        userId,
        traceId,
        idempotencyKey);

    ApiResponse<BaasTransferCreateResponse> response =
        bankCoreClient.createTransfer(userId, traceId, idempotencyKey, request);

    log.info(
        "[BaasTransferService] bank-server createTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransferApproveResponse> approveTransfer(
      Long userId, String traceId, String idempotencyKey, Long transferId) {
    log.info(
        "[BaasTransferService] approveTransfer 시작: userId={}, traceId={}, transferId={}",
        userId,
        traceId,
        transferId);

    ApiResponse<BaasTransferApproveResponse> response =
        bankCoreClient.approveTransfer(userId, traceId, idempotencyKey, transferId);

    log.info(
        "[BaasTransferService] bank-server approveTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransferDetailResponse> getTransfer(
      Long userId, String traceId, Long transferId) {
    log.info(
        "[BaasTransferService] getTransfer 시작: userId={}, traceId={}, transferId={}",
        userId,
        traceId,
        transferId);

    ApiResponse<BaasTransferDetailResponse> response =
        bankCoreClient.getTransfer(userId, traceId, transferId);

    log.info(
        "[BaasTransferService] bank-server getTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }
}
