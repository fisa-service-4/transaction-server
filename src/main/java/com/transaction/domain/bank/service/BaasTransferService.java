package com.transaction.domain.bank.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.domain.mapping.entity.TransferUserMapping;
import com.transaction.domain.mapping.repository.TransferUserMappingRepository;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasTransferService {

  private final BankCoreClient bankCoreClient;
  private final UserResolver userResolver;
  private final TransferUserMappingRepository transferUserMappingRepository;

  public ApiResponse<BaasTransferCreateResponse> createTransfer(
      String traceId, String idempotencyKey, BaasTransferRequest request) {
    Long xUserId = userResolver.resolveByAccount(request.getFromAccountId(), "BANK");
    log.info(
        "[BaasTransferService] createTransfer 시작: xUserId={}, traceId={}, idempotencyKey={}",
        xUserId,
        traceId,
        idempotencyKey);

    ApiResponse<BaasTransferCreateResponse> response =
        bankCoreClient.createTransfer(xUserId, traceId, idempotencyKey, request);

    Long transferId = response.getData().getTransferId();
    transferUserMappingRepository.save(new TransferUserMapping(transferId, xUserId));

    log.info(
        "[BaasTransferService] bank-server createTransfer 완료: transferId={}, status={}",
        transferId,
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransferApproveResponse> approveTransfer(
      String traceId, Long transferId) {
    Long xUserId = userResolver.resolveByTransferId(transferId);
    log.info(
        "[BaasTransferService] approveTransfer 시작: xUserId={}, traceId={}, transferId={}",
        xUserId,
        traceId,
        transferId);

    ApiResponse<BaasTransferApproveResponse> response =
        bankCoreClient.approveTransfer(xUserId, traceId, transferId);

    log.info(
        "[BaasTransferService] bank-server approveTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransferDetailResponse> getTransfer(String traceId, Long transferId) {
    Long xUserId = userResolver.resolveByTransferId(transferId);
    log.info(
        "[BaasTransferService] getTransfer 시작: xUserId={}, traceId={}, transferId={}",
        xUserId,
        traceId,
        transferId);

    ApiResponse<BaasTransferDetailResponse> response =
        bankCoreClient.getTransfer(xUserId, traceId, transferId);

    log.info(
        "[BaasTransferService] bank-server getTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }
}
