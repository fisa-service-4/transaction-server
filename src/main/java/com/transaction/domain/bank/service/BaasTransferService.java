package com.transaction.domain.bank.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.AccountValidateRequest;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.AccountValidateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.domain.mapping.entity.TransferUserMapping;
import com.transaction.domain.mapping.entity.UserAccountMappingId;
import com.transaction.domain.mapping.repository.TransferUserMappingRepository;
import com.transaction.domain.mapping.repository.UserAccountMappingRepository;
import com.transaction.domain.saga.service.SagaOrchestrator;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.response.BaasStockAccountItemResponse;
import com.transaction.global.config.BrokerCodeProperties;
import com.transaction.global.exception.SagaException;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
  private final BrokerCodeProperties brokerCodeProperties;

  public ApiResponse<BaasTransferCreateResponse> createTransfer(
      String traceId, String idempotencyKey, BaasTransferRequest request) {

    Long fromAccountId = request.getFromAccountId();
    String toBankCode = request.getToBankCode();
    String toAccountNumber = request.getToAccountNumber();

    // fromAccountId 타입 + toBankCode 타입 조합으로 라우팅 결정
    // fromIsStock: user_account_mapping에서 STOCK 여부 확인
    // toIsStock: application.yaml broker.codes에 등록된 증권사 코드 여부 (243, 247)
    boolean fromIsStock = isStockAccount(fromAccountId);
    boolean toIsStock =
        brokerCodeProperties.getCodes() != null
            && brokerCodeProperties.getCodes().contains(toBankCode);

    if (fromIsStock && toIsStock) {
      // STOCK → STOCK: 미지원
      throw new SagaException("SAGA_003", "증권 계좌 간 이체는 지원하지 않습니다.", HttpStatus.BAD_REQUEST);
    }

    // ─── STOCK_TO_BANK ─────────────────────────────────────────────────
    if (fromIsStock) {
      Long xUserId = userResolver.resolveByAccount(fromAccountId, "STOCK");
      log.info("[BaasTransferService] STOCK_TO_BANK 라우팅: xUserId={}, traceId={}", xUserId, traceId);

      AccountValidateResponse bankValidate =
          bankCoreClient
              .validateAccount(
                  xUserId, traceId, new AccountValidateRequest(toBankCode, toAccountNumber))
              .getData();
      if (bankValidate == null || !bankValidate.isValidYn()) {
        throw new SagaException("ACCOUNT_001", "유효하지 않은 은행 계좌입니다.", HttpStatus.BAD_REQUEST);
      }

      // stock-server deposit/withdraw는 accountNumber 기반 — accountId → accountNumber + brokerCode 변환
      BaasStockAccountItemResponse stockAccount =
          resolveStockAccount(xUserId, traceId, fromAccountId);
      String fromAccountNumber = stockAccount.getAccountNumber();
      String brokerCode = stockAccount.getBankCode();

      // 증권사 코드 기반 정산 계좌 조회 (243 → 2439999, 247 → 2479999)
      Long settlementAccountId = brokerCodeProperties.getSettlementAccountId(brokerCode);
      if (settlementAccountId == null) {
        throw new SagaException(
            "SAGA_004", "정산 계좌가 설정되지 않은 증권사입니다: " + brokerCode, HttpStatus.BAD_REQUEST);
      }

      Long settlementXUserId = brokerCodeProperties.getSettlementXUserId();
      if (settlementXUserId == null) {
        throw new SagaException(
            "SAGA_004", "정산 계좌 사용자 ID가 설정되지 않았습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
      }
      BaasTransferCreateResponse result =
          sagaOrchestrator.stockToBank(
              request,
              idempotencyKey,
              xUserId,
              traceId,
              fromAccountNumber,
              settlementAccountId,
              settlementXUserId);
      return ApiResponse.success(result, traceId);
    }

    Long xUserId = userResolver.resolveByAccount(fromAccountId, "BANK");

    // ─── BANK_TO_STOCK ─────────────────────────────────────────────────
    if (toIsStock) {
      log.info(
          "[BaasTransferService] BANK_TO_STOCK 라우팅: xUserId={}, toBankCode={}, traceId={}",
          xUserId,
          toBankCode,
          traceId);

      AccountValidateResponse stockValidate =
          stockCoreClient
              .validateAccount(
                  xUserId, traceId, new AccountValidateRequest(toBankCode, toAccountNumber))
              .getData();
      if (stockValidate == null || !stockValidate.isValidYn()) {
        throw new SagaException("ACCOUNT_001", "유효하지 않은 증권 계좌입니다.", HttpStatus.BAD_REQUEST);
      }

      BaasTransferCreateResponse result =
          sagaOrchestrator.bankToStock(request, idempotencyKey, xUserId, traceId, toAccountNumber);
      return ApiResponse.success(result, traceId);
    }

    // ─── bank-to-bank ───────────────────────────────────────────────────
    log.info("[BaasTransferService] bank-to-bank 이체: xUserId={}, traceId={}", xUserId, traceId);

    AccountValidateResponse btbValidate =
        bankCoreClient
            .validateAccount(
                xUserId, traceId, new AccountValidateRequest(toBankCode, toAccountNumber))
            .getData();
    if (btbValidate == null || !btbValidate.isValidYn()) {
      throw new SagaException("ACCOUNT_001", "유효하지 않은 은행 계좌입니다.", HttpStatus.BAD_REQUEST);
    }

    ApiResponse<BaasTransferCreateResponse> response =
        bankCoreClient.createTransfer(xUserId, traceId, idempotencyKey, request);

    Long transferId = response.getData().getTransferId();
    transferUserMappingRepository.save(new TransferUserMapping(transferId, xUserId));

    log.info(
        "[BaasTransferService] bank-to-bank 완료: transferId={}, status={}",
        transferId,
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransferApproveResponse> approveTransfer(String traceId, Long transferId) {
    Long xUserId = userResolver.resolveByTransferId(transferId);
    log.info(
        "[BaasTransferService] approveTransfer: xUserId={}, transferId={}", xUserId, transferId);

    ApiResponse<BaasTransferApproveResponse> response =
        bankCoreClient.approveTransfer(xUserId, traceId, transferId);

    log.info(
        "[BaasTransferService] approveTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<BaasTransferDetailResponse> getTransfer(String traceId, Long transferId) {
    Long xUserId = userResolver.resolveByTransferId(transferId);
    log.info("[BaasTransferService] getTransfer: xUserId={}, transferId={}", xUserId, transferId);

    ApiResponse<BaasTransferDetailResponse> response =
        bankCoreClient.getTransfer(xUserId, traceId, transferId);

    log.info(
        "[BaasTransferService] getTransfer 완료: transferId={}, status={}",
        response.getData().getTransferId(),
        response.getData().getTransferStatus());

    return ApiResponse.success(response.getData(), traceId);
  }

  private boolean isStockAccount(Long accountId) {
    return userAccountMappingRepository.existsById(new UserAccountMappingId(accountId, "STOCK"));
  }

  // fromAccountId → BaasStockAccountItemResponse 변환 — STOCK_TO_BANK 전용
  private BaasStockAccountItemResponse resolveStockAccount(
      Long xUserId, String traceId, Long fromAccountId) {
    return stockCoreClient.getStockAccounts(xUserId, traceId).getData().getContent().stream()
        .filter(a -> fromAccountId.equals(a.getAccountId()))
        .findFirst()
        .orElseThrow(
            () -> new SagaException("SAGA_002", "증권 계좌를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST));
  }
}
