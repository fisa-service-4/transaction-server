package com.transaction.domain.saga.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.domain.saga.enums.SagaStepName;
import com.transaction.domain.saga.enums.SagaStepStatus;
import com.transaction.domain.saga.enums.SagaType;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.request.StockCashRequest;
import com.transaction.global.exception.SagaException;
import com.transaction.global.service.AuditService;
import com.transaction.global.service.IdempotencyService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

  private final SagaStateManager sagaStateManager;
  private final IdempotencyService idempotencyService;
  private final AuditService auditService;
  private final BankCoreClient bankCoreClient;
  private final StockCoreClient stockCoreClient;
  private final ObjectMapper objectMapper;

  // ==================== BANK_TO_STOCK ====================
  //
  // 흐름: bank 이체 레코드 생성(STEP1) → stock 예수금 충전(STEP2) → bank 이체 확정(STEP3)
  //
  // STEP2를 STEP3보다 먼저 실행하는 이유:
  //   approve(STEP3)가 실패하면 stock deposit을 보상(차감)할 수 있음.
  //   반대로 approve 먼저 실행하면 bank 출금이 완료된 상태에서 stock deposit 실패 시
  //   bank 원복 수단이 없음 — 따라서 reversible한 stock deposit을 먼저 수행.
  //
  // 실패 시나리오:
  //   Case A: STEP2(stock deposit) 실패 → bank transfer REQUESTED 상태(잔액 변동 없음) → cancel 호출로 명시적 종료
  //   Case B: STEP3(bank approve) 실패 → stock deposit 이미 반영됨 → stock withdraw 보상 트랜잭션 실행
  //   UNKNOWN: STEP3 timeout → bank 상태 조회로 판단 → SUCCESS면 그대로, REQUESTED면 Case B 보상

  public BaasTransferCreateResponse bankToStock(
      BaasTransferRequest request,
      String idempotencyKey,
      Long xUserId,
      String traceId,
      String toAccountNumber) {

    Optional<String> cached =
        idempotencyService.check(idempotencyKey, toJson(request), "BANK_TO_STOCK");
    if (cached.isPresent()) {
      return fromJson(cached.get(), BaasTransferCreateResponse.class);
    }

    var saga =
        sagaStateManager.initSaga(
            SagaType.BANK_TO_STOCK,
            idempotencyKey,
            String.format(
                "{\"sagaType\":\"BANK_TO_STOCK\",\"amount\":%s}", request.getTransferAmount()));

    Long transferId = null;

    // ─── STEP 1: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
    // 이체 레코드만 생성. 잔액 변동 없음 (approve 시점에 실제 출금)
    try {
      var createResp =
          bankCoreClient.createTransfer(xUserId, traceId, idempotencyKey, request).getData();
      transferId = createResp.getTransferId();
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.BANK_TRANSFER_REQUEST_CREATED,
          1,
          SagaStepStatus.SUCCESS,
          toJson(request),
          toJson(createResp),
          null);
      log.info("[Saga-BTS] STEP 1 SUCCESS: sagaId={}, transferId={}", saga.getSagaId(), transferId);
    } catch (Exception e) {
      log.error("[Saga-BTS] STEP 1 FAILED: sagaId={}, error={}", saga.getSagaId(), e.getMessage());
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.BANK_TRANSFER_REQUEST_CREATED,
          1,
          SagaStepStatus.FAILED,
          toJson(request),
          null,
          e.getMessage());
      sagaStateManager.failSaga(
          saga.getSagaId(), idempotencyKey, e.getMessage(), failPayload(saga.getSagaId(), "STEP1"));
      throw new SagaException("이체 요청 생성 실패: " + e.getMessage(), e);
    }

    // ─── STEP 2: STOCK_CASH_DEPOSIT ──────────────────────────────────
    // 실패 시(Case A): bank transfer가 REQUESTED 상태이므로 잔액 변동 없음 → cancel 호출로 정리
    String depositKey = saga.getSagaId() + "_DEPOSIT";
    StockCashRequest cashReq =
        new StockCashRequest(toAccountNumber, request.getTransferAmount().longValue());
    try {
      stockCoreClient.depositCash(xUserId, traceId, depositKey, cashReq);
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.STOCK_CASH_DEPOSIT,
          2,
          SagaStepStatus.SUCCESS,
          toJson(cashReq),
          null,
          null);
      log.info("[Saga-BTS] STEP 2 SUCCESS: sagaId={}", saga.getSagaId());
    } catch (Exception e) {
      log.error(
          "[Saga-BTS] STEP 2 FAILED (Case A): sagaId={}, error={}",
          saga.getSagaId(),
          e.getMessage());
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.STOCK_CASH_DEPOSIT,
          2,
          SagaStepStatus.FAILED,
          toJson(cashReq),
          null,
          e.getMessage());
      sagaStateManager.failSaga(
          saga.getSagaId(), idempotencyKey, e.getMessage(), failPayload(saga.getSagaId(), "STEP2"));
      tryCancelTransfer(xUserId, traceId, transferId, saga.getSagaId());
      throw new SagaException("증권 예수금 충전 실패: " + e.getMessage(), e);
    }

    // ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
    return executeApproveStep(
        request,
        saga.getSagaId(),
        idempotencyKey,
        xUserId,
        traceId,
        transferId,
        toAccountNumber,
        request.getTransferAmount().longValue());
  }

  private BaasTransferCreateResponse executeApproveStep(
      BaasTransferRequest originalRequest,
      Long sagaId,
      String idempotencyKey,
      Long xUserId,
      String traceId,
      Long transferId,
      String toAccountNumber,
      Long amount) {

    try {
      bankCoreClient.approveTransfer(xUserId, traceId, transferId);
      sagaStateManager.recordStep(
          sagaId,
          SagaStepName.BANK_TRANSFER_COMMIT,
          3,
          SagaStepStatus.SUCCESS,
          String.valueOf(transferId),
          null,
          null);
      BaasTransferCreateResponse result =
          new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
      sagaStateManager.completeSaga(
          sagaId,
          idempotencyKey,
          toJson(result),
          String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
      auditService.record(
          "BANK_TO_STOCK",
          idempotencyKey,
          "SUCCESS",
          "transaction-server",
          "bank-server",
          toJson(originalRequest),
          toJson(result));
      log.info("[Saga-BTS] STEP 3 SUCCESS: sagaId={}, transferId={}", sagaId, transferId);
      return result;

    } catch (Exception approveEx) {
      // approve 예외 발생 시 즉시 실패로 처리하지 않고 bank 상태를 직접 조회해서 판단.
      // 네트워크 timeout인 경우 bank가 실제로 처리를 완료했을 수 있기 때문.
      log.warn("[Saga-BTS] STEP 3 exception: sagaId={}, error={}", sagaId, approveEx.getMessage());
      try {
        BaasTransferDetailResponse status =
            bankCoreClient.getTransfer(xUserId, traceId, transferId).getData();
        String transferStatus = status.getTransferStatus();

        if ("SUCCESS".equals(transferStatus) || "COMPLETED".equals(transferStatus)) {
          // bank가 실제로 처리 완료 → Saga SUCCESS로 확정
          log.info("[Saga-BTS] STEP 3 recovered (bank SUCCESS): sagaId={}", sagaId);
          sagaStateManager.recordStep(
              sagaId,
              SagaStepName.BANK_TRANSFER_COMMIT,
              3,
              SagaStepStatus.SUCCESS,
              String.valueOf(transferId),
              null,
              null);
          BaasTransferCreateResponse result =
              new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
          sagaStateManager.completeSaga(
              sagaId,
              idempotencyKey,
              toJson(result),
              String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
          return result;

        } else {
          // bank가 REQUESTED 상태 → 출금 미처리, stock deposit은 이미 반영됨 → Case B 보상
          log.warn(
              "[Saga-BTS] STEP 3 FAILED (Case B): sagaId={}, bankStatus={}",
              sagaId,
              transferStatus);
          return compensateBankToStock(
              sagaId,
              idempotencyKey,
              xUserId,
              traceId,
              transferId,
              toAccountNumber,
              amount,
              approveEx.getMessage());
        }

      } catch (Exception queryEx) {
        // 상태 조회마저 실패 → bank 처리 여부 불명 (UNKNOWN)
        // stock deposit과 bank approve 양쪽 모두 결과 불확실 → 수동 개입 필요
        log.error(
            "[Saga-BTS] STEP 3 UNKNOWN: sagaId={}, queryError={}", sagaId, queryEx.getMessage());
        sagaStateManager.recordStep(
            sagaId,
            SagaStepName.BANK_TRANSFER_COMMIT,
            3,
            SagaStepStatus.FAILED,
            String.valueOf(transferId),
            null,
            approveEx.getMessage());
        sagaStateManager.unknownSaga(
            sagaId,
            idempotencyKey,
            approveEx.getMessage(),
            String.format("{\"sagaId\":%d,\"reason\":\"approve timeout\"}", sagaId));
        throw new SagaException("이체 승인 결과를 확인할 수 없습니다. 운영팀 확인이 필요합니다.", approveEx);
      }
    }
  }

  private BaasTransferCreateResponse compensateBankToStock(
      Long sagaId,
      String idempotencyKey,
      Long xUserId,
      String traceId,
      Long transferId,
      String toAccountNumber,
      Long amount,
      String originalError) {

    sagaStateManager.recordStep(
        sagaId,
        SagaStepName.BANK_TRANSFER_COMMIT,
        3,
        SagaStepStatus.FAILED,
        String.valueOf(transferId),
        null,
        originalError);
    sagaStateManager.startCompensation(
        sagaId, String.format("{\"sagaId\":%d,\"compensatingStep\":\"STOCK_WITHDRAW\"}", sagaId));

    try {
      String withdrawKey = sagaId + "_COMPENSATION_WITHDRAW";
      StockCashRequest withdrawReq = new StockCashRequest(toAccountNumber, amount);
      stockCoreClient.withdrawCash(xUserId, traceId, withdrawKey, withdrawReq);
      sagaStateManager.recordStep(
          sagaId,
          SagaStepName.STOCK_CASH_WITHDRAW_COMPENSATION,
          4,
          SagaStepStatus.COMPENSATED,
          toJson(withdrawReq),
          null,
          null);
      sagaStateManager.compensatedSaga(
          sagaId,
          idempotencyKey,
          String.format("{\"sagaId\":%d,\"compensatedStep\":\"STOCK_WITHDRAW\"}", sagaId));
      log.info("[Saga-BTS] Compensation SUCCESS: sagaId={}", sagaId);
    } catch (Exception compEx) {
      log.error("[Saga-BTS] Compensation FAILED: sagaId={}, error={}", sagaId, compEx.getMessage());
      sagaStateManager.compensationFailedSaga(sagaId, idempotencyKey, compEx.getMessage());
    }

    throw new SagaException("이체 확정 실패. 증권 예수금이 원복 처리됐습니다.");
  }

  // ==================== STOCK_TO_BANK ====================
  //
  // 흐름: stock 예수금 차감(STEP1) → bank 이체 레코드 생성(STEP2) → bank 이체 확정(STEP3)
  //
  // STEP1 실패: stock 변동 없음, bank 미호출 → 보상 없이 FAILED 종료
  // STEP2/3 실패: stock 예수금 이미 차감됨 → stock deposit 보상 트랜잭션으로 원복

  public BaasTransferCreateResponse stockToBank(
      BaasTransferRequest request,
      String idempotencyKey,
      Long xUserId,
      String traceId,
      String fromAccountNumber) {

    Optional<String> cached =
        idempotencyService.check(idempotencyKey, toJson(request), "STOCK_TO_BANK");
    if (cached.isPresent()) {
      return fromJson(cached.get(), BaasTransferCreateResponse.class);
    }

    var saga =
        sagaStateManager.initSaga(
            SagaType.STOCK_TO_BANK,
            idempotencyKey,
            String.format(
                "{\"sagaType\":\"STOCK_TO_BANK\",\"amount\":%s}", request.getTransferAmount()));

    // ─── STEP 1: STOCK_CASH_WITHDRAW ─────────────────────────────────
    String withdrawKey = saga.getSagaId() + "_WITHDRAW";
    StockCashRequest withdrawReq =
        new StockCashRequest(fromAccountNumber, request.getTransferAmount().longValue());
    try {
      stockCoreClient.withdrawCash(xUserId, traceId, withdrawKey, withdrawReq);
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.STOCK_CASH_WITHDRAW,
          1,
          SagaStepStatus.SUCCESS,
          toJson(withdrawReq),
          null,
          null);
      log.info("[Saga-STB] STEP 1 SUCCESS: sagaId={}", saga.getSagaId());
    } catch (Exception e) {
      log.error("[Saga-STB] STEP 1 FAILED: sagaId={}, error={}", saga.getSagaId(), e.getMessage());
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.STOCK_CASH_WITHDRAW,
          1,
          SagaStepStatus.FAILED,
          toJson(withdrawReq),
          null,
          e.getMessage());
      sagaStateManager.failSaga(
          saga.getSagaId(), idempotencyKey, e.getMessage(), failPayload(saga.getSagaId(), "STEP1"));
      throw new SagaException("증권 예수금 출금 실패: " + e.getMessage(), e);
    }

    Long transferId = null;

    // ─── STEP 2: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
    try {
      var createResp =
          bankCoreClient.createTransfer(xUserId, traceId, idempotencyKey, request).getData();
      transferId = createResp.getTransferId();
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.BANK_TRANSFER_REQUEST_CREATED,
          2,
          SagaStepStatus.SUCCESS,
          toJson(request),
          toJson(createResp),
          null);
      log.info("[Saga-STB] STEP 2 SUCCESS: sagaId={}, transferId={}", saga.getSagaId(), transferId);
    } catch (Exception e) {
      log.error("[Saga-STB] STEP 2 FAILED: sagaId={}, error={}", saga.getSagaId(), e.getMessage());
      sagaStateManager.recordStep(
          saga.getSagaId(),
          SagaStepName.BANK_TRANSFER_REQUEST_CREATED,
          2,
          SagaStepStatus.FAILED,
          toJson(request),
          null,
          e.getMessage());
      return compensateStockToBank(
          saga.getSagaId(),
          idempotencyKey,
          xUserId,
          traceId,
          fromAccountNumber,
          request.getTransferAmount().longValue(),
          e.getMessage());
    }

    // ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
    return executeApproveStepStockToBank(
        request,
        saga.getSagaId(),
        idempotencyKey,
        xUserId,
        traceId,
        transferId,
        fromAccountNumber,
        request.getTransferAmount().longValue());
  }

  private BaasTransferCreateResponse executeApproveStepStockToBank(
      BaasTransferRequest originalRequest,
      Long sagaId,
      String idempotencyKey,
      Long xUserId,
      String traceId,
      Long transferId,
      String fromAccountNumber,
      Long amount) {

    try {
      bankCoreClient.approveTransfer(xUserId, traceId, transferId);
      sagaStateManager.recordStep(
          sagaId,
          SagaStepName.BANK_TRANSFER_COMMIT,
          3,
          SagaStepStatus.SUCCESS,
          String.valueOf(transferId),
          null,
          null);
      BaasTransferCreateResponse result =
          new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
      sagaStateManager.completeSaga(
          sagaId,
          idempotencyKey,
          toJson(result),
          String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
      auditService.record(
          "STOCK_TO_BANK",
          idempotencyKey,
          "SUCCESS",
          "transaction-server",
          "bank-server",
          toJson(originalRequest),
          toJson(result));
      log.info("[Saga-STB] STEP 3 SUCCESS: sagaId={}, transferId={}", sagaId, transferId);
      return result;

    } catch (Exception approveEx) {
      log.warn("[Saga-STB] STEP 3 exception: sagaId={}, error={}", sagaId, approveEx.getMessage());
      try {
        BaasTransferDetailResponse status =
            bankCoreClient.getTransfer(xUserId, traceId, transferId).getData();
        String transferStatus = status.getTransferStatus();

        if ("SUCCESS".equals(transferStatus) || "COMPLETED".equals(transferStatus)) {
          log.info("[Saga-STB] STEP 3 recovered (bank SUCCESS): sagaId={}", sagaId);
          sagaStateManager.recordStep(
              sagaId,
              SagaStepName.BANK_TRANSFER_COMMIT,
              3,
              SagaStepStatus.SUCCESS,
              String.valueOf(transferId),
              null,
              null);
          BaasTransferCreateResponse result =
              new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
          sagaStateManager.completeSaga(
              sagaId,
              idempotencyKey,
              toJson(result),
              String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
          return result;
        } else {
          log.warn("[Saga-STB] STEP 3 FAILED, compensating: sagaId={}", sagaId);
          return compensateStockToBank(
              sagaId,
              idempotencyKey,
              xUserId,
              traceId,
              fromAccountNumber,
              amount,
              approveEx.getMessage());
        }

      } catch (Exception queryEx) {
        log.error(
            "[Saga-STB] STEP 3 UNKNOWN: sagaId={}, queryError={}", sagaId, queryEx.getMessage());
        sagaStateManager.recordStep(
            sagaId,
            SagaStepName.BANK_TRANSFER_COMMIT,
            3,
            SagaStepStatus.FAILED,
            String.valueOf(transferId),
            null,
            approveEx.getMessage());
        sagaStateManager.unknownSaga(
            sagaId,
            idempotencyKey,
            approveEx.getMessage(),
            String.format("{\"sagaId\":%d,\"reason\":\"approve timeout\"}", sagaId));
        throw new SagaException("이체 승인 결과를 확인할 수 없습니다. 운영팀 확인이 필요합니다.", approveEx);
      }
    }
  }

  private BaasTransferCreateResponse compensateStockToBank(
      Long sagaId,
      String idempotencyKey,
      Long xUserId,
      String traceId,
      String fromAccountNumber,
      Long amount,
      String originalError) {

    sagaStateManager.startCompensation(
        sagaId, String.format("{\"sagaId\":%d,\"compensatingStep\":\"STOCK_DEPOSIT\"}", sagaId));

    try {
      String depositKey = sagaId + "_COMPENSATION_DEPOSIT";
      StockCashRequest depositReq = new StockCashRequest(fromAccountNumber, amount);
      stockCoreClient.depositCash(xUserId, traceId, depositKey, depositReq);
      sagaStateManager.recordStep(
          sagaId,
          SagaStepName.STOCK_CASH_DEPOSIT_COMPENSATION,
          4,
          SagaStepStatus.COMPENSATED,
          toJson(depositReq),
          null,
          null);
      sagaStateManager.compensatedSaga(
          sagaId,
          idempotencyKey,
          String.format("{\"sagaId\":%d,\"compensatedStep\":\"STOCK_DEPOSIT\"}", sagaId));
      log.info("[Saga-STB] Compensation SUCCESS: sagaId={}", sagaId);
    } catch (Exception compEx) {
      log.error("[Saga-STB] Compensation FAILED: sagaId={}, error={}", sagaId, compEx.getMessage());
      sagaStateManager.compensationFailedSaga(sagaId, idempotencyKey, compEx.getMessage());
    }

    throw new SagaException("이체 처리 실패. 증권 예수금이 원복 처리됐습니다.");
  }

  // ==================== helpers ====================

  private void tryCancelTransfer(Long xUserId, String traceId, Long transferId, Long sagaId) {
    try {
      bankCoreClient.cancelTransfer(xUserId, traceId, transferId);
      log.info("[Saga] bank transfer cancelled: sagaId={}, transferId={}", sagaId, transferId);
    } catch (Exception e) {
      log.warn(
          "[Saga] bank transfer cancel 실패 (무시): sagaId={}, transferId={}, error={}",
          sagaId,
          transferId,
          e.getMessage());
    }
  }

  private String failPayload(Long sagaId, String failedStep) {
    return String.format("{\"sagaId\":%d,\"failedStep\":\"%s\"}", sagaId, failedStep);
  }

  private String toJson(Object obj) {
    if (obj == null) return null;
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return obj.toString();
    }
  }

  private <T> T fromJson(String json, Class<T> clazz) {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new SagaException("캐시된 응답 역직렬화 실패", e);
    }
  }
}
