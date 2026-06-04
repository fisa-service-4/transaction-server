package com.transaction.domain.bank.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.domain.mapping.entity.TransferUserMapping;
import com.transaction.domain.mapping.entity.UserAccountMappingId;
import com.transaction.domain.mapping.repository.TransferUserMappingRepository;
import com.transaction.domain.mapping.repository.UserAccountMappingRepository;
import com.transaction.domain.saga.service.SagaOrchestrator;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.global.exception.SagaException;
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
  private final StockCoreClient stockCoreClient;
  private final UserResolver userResolver;
  private final TransferUserMappingRepository transferUserMappingRepository;
  private final UserAccountMappingRepository userAccountMappingRepository;
  private final SagaOrchestrator sagaOrchestrator;

  public ApiResponse<BaasTransferCreateResponse> createTransfer(
      String traceId, String idempotencyKey, BaasTransferRequest request) {

    Long fromAccountId = request.getFromAccountId();

    // STOCK_TO_BANK: fromAccountId가 증권 계좌인 경우
    if (isStockAccount(fromAccountId)) {
      Long xUserId = userResolver.resolveByAccount(fromAccountId, "STOCK");
      log.info("[BaasTransferService] STOCK_TO_BANK Saga 라우팅: xUserId={}, traceId={}", xUserId, traceId);
      BaasTransferCreateResponse result = sagaOrchestrator.stockToBank(request, idempotencyKey, xUserId, traceId);
      return ApiResponse.success(result, traceId);
    }

    // BANK_TO_STOCK: fromAccountId가 은행 계좌이고 toAccountNumber가 증권 계좌인 경우
    if (isBankAccount(fromAccountId)) {
      Long xUserId = userResolver.resolveByAccount(fromAccountId, "BANK");
      Long toStockAccountId = findToStockAccountId(xUserId, traceId, request.getToAccountNumber());
      if (toStockAccountId != null) {
        log.info("[BaasTransferService] BANK_TO_STOCK Saga 라우팅: xUserId={}, toStockAccountId={}, traceId={}",
            xUserId, toStockAccountId, traceId);
        BaasTransferCreateResponse result =
            sagaOrchestrator.bankToStock(request, idempotencyKey, xUserId, traceId, toStockAccountId);
        return ApiResponse.success(result, traceId);
      }
    }

    // 기존 bank-to-bank 이체
    Long xUserId = userResolver.resolveByAccount(fromAccountId, "BANK");
    log.info(
        "[BaasTransferService] bank-to-bank 이체: xUserId={}, traceId={}, idempotencyKey={}",
        xUserId, traceId, idempotencyKey);

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

  private boolean isStockAccount(Long accountId) {
    return userAccountMappingRepository.existsById(new UserAccountMappingId(accountId, "STOCK"));
  }

  private boolean isBankAccount(Long accountId) {
    return userAccountMappingRepository.existsById(new UserAccountMappingId(accountId, "BANK"));
  }

  private Long findToStockAccountId(Long xUserId, String traceId, String toAccountNumber) {
    try {
      return sagaOrchestrator.findStockAccountId(xUserId, traceId, toAccountNumber);
    } catch (SagaException e) {
      return null;
    }
  }

  public ApiResponse<BaasTransferApproveResponse> approveTransfer(String traceId, Long transferId) {
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
