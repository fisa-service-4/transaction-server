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
import com.transaction.domain.stock.dto.response.BaasStockAccountItemResponse;
import com.transaction.global.exception.SagaException;
import com.transaction.global.service.AuditService;
import com.transaction.global.service.IdempotencyService;
import java.time.LocalDateTime;
import java.util.List;
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

    public BaasTransferCreateResponse bankToStock(
            BaasTransferRequest request,
            String idempotencyKey,
            Long xUserId,
            String traceId,
            Long toStockAccountId) {

        Optional<String> cached = idempotencyService.check(idempotencyKey, toJson(request), "BANK_TO_STOCK");
        if (cached.isPresent()) {
            return fromJson(cached.get(), BaasTransferCreateResponse.class);
        }

        var saga = sagaStateManager.initSaga(
                SagaType.BANK_TO_STOCK, idempotencyKey,
                String.format("{\"sagaType\":\"BANK_TO_STOCK\",\"amount\":%s}", request.getTransferAmount()));

        Long transferId = null;

        // ─── STEP 1: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
        try {
            var createResp = bankCoreClient
                    .createTransfer(xUserId, traceId, idempotencyKey, request)
                    .getData();
            transferId = createResp.getTransferId();
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.BANK_TRANSFER_REQUEST_CREATED, 1, SagaStepStatus.SUCCESS,
                    toJson(request), toJson(createResp), null);
            log.info("[Saga-BTS] STEP 1 SUCCESS: sagaId={}, transferId={}", saga.getSagaId(), transferId);
        } catch (Exception e) {
            log.error("[Saga-BTS] STEP 1 FAILED: sagaId={}, error={}", saga.getSagaId(), e.getMessage());
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.BANK_TRANSFER_REQUEST_CREATED, 1, SagaStepStatus.FAILED,
                    toJson(request), null, e.getMessage());
            sagaStateManager.failSaga(saga.getSagaId(), idempotencyKey, e.getMessage(),
                    failPayload(saga.getSagaId(), "STEP1"));
            throw new SagaException("이체 요청 생성 실패: " + e.getMessage(), e);
        }

        // ─── STEP 2: STOCK_CASH_DEPOSIT ──────────────────────────────────
        String depositKey = saga.getSagaId() + "_DEPOSIT";
        StockCashRequest cashReq = new StockCashRequest(request.getTransferAmount().longValue());
        try {
            stockCoreClient.depositCash(xUserId, traceId, depositKey, toStockAccountId, cashReq);
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.STOCK_CASH_DEPOSIT, 2, SagaStepStatus.SUCCESS,
                    toJson(cashReq), null, null);
            log.info("[Saga-BTS] STEP 2 SUCCESS: sagaId={}", saga.getSagaId());
        } catch (Exception e) {
            log.error("[Saga-BTS] STEP 2 FAILED (Case A): sagaId={}, error={}", saga.getSagaId(), e.getMessage());
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.STOCK_CASH_DEPOSIT, 2, SagaStepStatus.FAILED,
                    toJson(cashReq), null, e.getMessage());
            sagaStateManager.failSaga(saga.getSagaId(), idempotencyKey, e.getMessage(),
                    failPayload(saga.getSagaId(), "STEP2"));
            tryCancelTransfer(xUserId, traceId, transferId, saga.getSagaId());
            throw new SagaException("증권 예수금 충전 실패: " + e.getMessage(), e);
        }

        // ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
        return executeApproveStep(request, saga.getSagaId(), idempotencyKey,
                xUserId, traceId, transferId, toStockAccountId,
                request.getTransferAmount().longValue());
    }

    private BaasTransferCreateResponse executeApproveStep(
            BaasTransferRequest originalRequest,
            Long sagaId,
            String idempotencyKey,
            Long xUserId,
            String traceId,
            Long transferId,
            Long toStockAccountId,
            Long amount) {

        try {
            bankCoreClient.approveTransfer(xUserId, traceId, transferId);
            sagaStateManager.recordStep(sagaId,
                    SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.SUCCESS,
                    String.valueOf(transferId), null, null);
            BaasTransferCreateResponse result =
                    new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
            sagaStateManager.completeSaga(sagaId, idempotencyKey, toJson(result),
                    String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
            auditService.record("BANK_TO_STOCK", idempotencyKey, "SUCCESS",
                    "transaction-server", "bank-server", toJson(originalRequest), toJson(result));
            log.info("[Saga-BTS] STEP 3 SUCCESS: sagaId={}, transferId={}", sagaId, transferId);
            return result;

        } catch (Exception approveEx) {
            log.warn("[Saga-BTS] STEP 3 exception: sagaId={}, error={}", sagaId, approveEx.getMessage());
            // approve 결과 불명 → 상태 조회로 판단
            try {
                BaasTransferDetailResponse status =
                        bankCoreClient.getTransfer(xUserId, traceId, transferId).getData();
                String transferStatus = status.getTransferStatus();

                if ("SUCCESS".equals(transferStatus) || "COMPLETED".equals(transferStatus)) {
                    // bank가 실제로 처리 완료
                    log.info("[Saga-BTS] STEP 3 recovered (bank SUCCESS): sagaId={}", sagaId);
                    sagaStateManager.recordStep(sagaId,
                            SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.SUCCESS,
                            String.valueOf(transferId), null, null);
                    BaasTransferCreateResponse result =
                            new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
                    sagaStateManager.completeSaga(sagaId, idempotencyKey, toJson(result),
                            String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
                    return result;

                } else {
                    // REQUESTED or FAILED → Case B: 보상
                    log.warn("[Saga-BTS] STEP 3 FAILED (Case B): sagaId={}, bankStatus={}", sagaId, transferStatus);
                    return compensateBankToStock(sagaId, idempotencyKey, xUserId, traceId,
                            transferId, toStockAccountId, amount, approveEx.getMessage());
                }

            } catch (Exception queryEx) {
                // 상태 조회도 실패 → UNKNOWN
                log.error("[Saga-BTS] STEP 3 UNKNOWN: sagaId={}, queryError={}", sagaId, queryEx.getMessage());
                sagaStateManager.recordStep(sagaId,
                        SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.FAILED,
                        String.valueOf(transferId), null, approveEx.getMessage());
                sagaStateManager.unknownSaga(sagaId, idempotencyKey, approveEx.getMessage(),
                        String.format("{\"sagaId\":%d,\"reason\":\"approve timeout\"}", sagaId));
                throw new SagaException("이체 승인 결과를 확인할 수 없습니다. 운영팀 확인이 필요합니다.", approveEx);
            }
        }
    }

    private BaasTransferCreateResponse compensateBankToStock(
            Long sagaId, String idempotencyKey,
            Long xUserId, String traceId,
            Long transferId, Long toStockAccountId, Long amount, String originalError) {

        sagaStateManager.recordStep(sagaId,
                SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.FAILED,
                String.valueOf(transferId), null, originalError);
        sagaStateManager.startCompensation(sagaId,
                String.format("{\"sagaId\":%d,\"compensatingStep\":\"STOCK_WITHDRAW\"}", sagaId));

        try {
            String withdrawKey = sagaId + "_COMPENSATION_WITHDRAW";
            StockCashRequest withdrawReq = new StockCashRequest(amount);
            stockCoreClient.withdrawCash(xUserId, traceId, withdrawKey, toStockAccountId, withdrawReq);
            sagaStateManager.recordStep(sagaId,
                    SagaStepName.STOCK_CASH_WITHDRAW_COMPENSATION, 4, SagaStepStatus.COMPENSATED,
                    toJson(withdrawReq), null, null);
            sagaStateManager.compensatedSaga(sagaId, idempotencyKey,
                    String.format("{\"sagaId\":%d,\"compensatedStep\":\"STOCK_WITHDRAW\"}", sagaId));
            log.info("[Saga-BTS] Compensation SUCCESS: sagaId={}", sagaId);
        } catch (Exception compEx) {
            log.error("[Saga-BTS] Compensation FAILED: sagaId={}, error={}", sagaId, compEx.getMessage());
            sagaStateManager.compensationFailedSaga(sagaId, idempotencyKey, compEx.getMessage());
        }

        throw new SagaException("이체 확정 실패. 증권 예수금이 원복 처리됐습니다.");
    }

    // ==================== STOCK_TO_BANK ====================

    public BaasTransferCreateResponse stockToBank(
            BaasTransferRequest request,
            String idempotencyKey,
            Long xUserId,
            String traceId) {

        Optional<String> cached = idempotencyService.check(idempotencyKey, toJson(request), "STOCK_TO_BANK");
        if (cached.isPresent()) {
            return fromJson(cached.get(), BaasTransferCreateResponse.class);
        }

        var saga = sagaStateManager.initSaga(
                SagaType.STOCK_TO_BANK, idempotencyKey,
                String.format("{\"sagaType\":\"STOCK_TO_BANK\",\"amount\":%s}", request.getTransferAmount()));

        Long fromStockAccountId = request.getFromAccountId();

        // ─── STEP 1: STOCK_CASH_WITHDRAW ─────────────────────────────────
        String withdrawKey = saga.getSagaId() + "_WITHDRAW";
        StockCashRequest withdrawReq = new StockCashRequest(request.getTransferAmount().longValue());
        try {
            stockCoreClient.withdrawCash(xUserId, traceId, withdrawKey, fromStockAccountId, withdrawReq);
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.STOCK_CASH_WITHDRAW, 1, SagaStepStatus.SUCCESS,
                    toJson(withdrawReq), null, null);
            log.info("[Saga-STB] STEP 1 SUCCESS: sagaId={}", saga.getSagaId());
        } catch (Exception e) {
            log.error("[Saga-STB] STEP 1 FAILED: sagaId={}, error={}", saga.getSagaId(), e.getMessage());
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.STOCK_CASH_WITHDRAW, 1, SagaStepStatus.FAILED,
                    toJson(withdrawReq), null, e.getMessage());
            sagaStateManager.failSaga(saga.getSagaId(), idempotencyKey, e.getMessage(),
                    failPayload(saga.getSagaId(), "STEP1"));
            throw new SagaException("증권 예수금 출금 실패: " + e.getMessage(), e);
        }

        Long transferId = null;

        // ─── STEP 2: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
        try {
            var createResp = bankCoreClient
                    .createTransfer(xUserId, traceId, idempotencyKey, request)
                    .getData();
            transferId = createResp.getTransferId();
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.BANK_TRANSFER_REQUEST_CREATED, 2, SagaStepStatus.SUCCESS,
                    toJson(request), toJson(createResp), null);
            log.info("[Saga-STB] STEP 2 SUCCESS: sagaId={}, transferId={}", saga.getSagaId(), transferId);
        } catch (Exception e) {
            log.error("[Saga-STB] STEP 2 FAILED: sagaId={}, error={}", saga.getSagaId(), e.getMessage());
            sagaStateManager.recordStep(saga.getSagaId(),
                    SagaStepName.BANK_TRANSFER_REQUEST_CREATED, 2, SagaStepStatus.FAILED,
                    toJson(request), null, e.getMessage());
            return compensateStockToBank(saga.getSagaId(), idempotencyKey, xUserId, traceId,
                    fromStockAccountId, request.getTransferAmount().longValue(), e.getMessage());
        }

        // ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
        return executeApproveStepStockToBank(request, saga.getSagaId(), idempotencyKey,
                xUserId, traceId, transferId, fromStockAccountId,
                request.getTransferAmount().longValue());
    }

    private BaasTransferCreateResponse executeApproveStepStockToBank(
            BaasTransferRequest originalRequest,
            Long sagaId,
            String idempotencyKey,
            Long xUserId,
            String traceId,
            Long transferId,
            Long fromStockAccountId,
            Long amount) {

        try {
            bankCoreClient.approveTransfer(xUserId, traceId, transferId);
            sagaStateManager.recordStep(sagaId,
                    SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.SUCCESS,
                    String.valueOf(transferId), null, null);
            BaasTransferCreateResponse result =
                    new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
            sagaStateManager.completeSaga(sagaId, idempotencyKey, toJson(result),
                    String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
            auditService.record("STOCK_TO_BANK", idempotencyKey, "SUCCESS",
                    "transaction-server", "bank-server", toJson(originalRequest), toJson(result));
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
                    sagaStateManager.recordStep(sagaId,
                            SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.SUCCESS,
                            String.valueOf(transferId), null, null);
                    BaasTransferCreateResponse result =
                            new BaasTransferCreateResponse(transferId, "SUCCESS", LocalDateTime.now());
                    sagaStateManager.completeSaga(sagaId, idempotencyKey, toJson(result),
                            String.format("{\"sagaId\":%d,\"transferId\":%d}", sagaId, transferId));
                    return result;
                } else {
                    log.warn("[Saga-STB] STEP 3 FAILED, compensating: sagaId={}", sagaId);
                    return compensateStockToBank(sagaId, idempotencyKey, xUserId, traceId,
                            fromStockAccountId, amount, approveEx.getMessage());
                }

            } catch (Exception queryEx) {
                log.error("[Saga-STB] STEP 3 UNKNOWN: sagaId={}, queryError={}", sagaId, queryEx.getMessage());
                sagaStateManager.recordStep(sagaId,
                        SagaStepName.BANK_TRANSFER_COMMIT, 3, SagaStepStatus.FAILED,
                        String.valueOf(transferId), null, approveEx.getMessage());
                sagaStateManager.unknownSaga(sagaId, idempotencyKey, approveEx.getMessage(),
                        String.format("{\"sagaId\":%d,\"reason\":\"approve timeout\"}", sagaId));
                throw new SagaException("이체 승인 결과를 확인할 수 없습니다. 운영팀 확인이 필요합니다.", approveEx);
            }
        }
    }

    private BaasTransferCreateResponse compensateStockToBank(
            Long sagaId, String idempotencyKey,
            Long xUserId, String traceId,
            Long fromStockAccountId, Long amount, String originalError) {

        sagaStateManager.startCompensation(sagaId,
                String.format("{\"sagaId\":%d,\"compensatingStep\":\"STOCK_DEPOSIT\"}", sagaId));

        try {
            String depositKey = sagaId + "_COMPENSATION_DEPOSIT";
            StockCashRequest depositReq = new StockCashRequest(amount);
            stockCoreClient.depositCash(xUserId, traceId, depositKey, fromStockAccountId, depositReq);
            sagaStateManager.recordStep(sagaId,
                    SagaStepName.STOCK_CASH_DEPOSIT_COMPENSATION, 4, SagaStepStatus.COMPENSATED,
                    toJson(depositReq), null, null);
            sagaStateManager.compensatedSaga(sagaId, idempotencyKey,
                    String.format("{\"sagaId\":%d,\"compensatedStep\":\"STOCK_DEPOSIT\"}", sagaId));
            log.info("[Saga-STB] Compensation SUCCESS: sagaId={}", sagaId);
        } catch (Exception compEx) {
            log.error("[Saga-STB] Compensation FAILED: sagaId={}, error={}", sagaId, compEx.getMessage());
            sagaStateManager.compensationFailedSaga(sagaId, idempotencyKey, compEx.getMessage());
        }

        throw new SagaException("이체 처리 실패. 증권 예수금이 원복 처리됐습니다.");
    }

    // ==================== helpers ====================

    public Long findStockAccountId(Long xUserId, String traceId, String toAccountNumber) {
        List<BaasStockAccountItemResponse> accounts =
                stockCoreClient.getStockAccounts(xUserId, traceId).getData().getContent();
        return accounts.stream()
                .filter(a -> toAccountNumber.equals(a.getAccountNumber()))
                .findFirst()
                .map(BaasStockAccountItemResponse::getAccountId)
                .orElseThrow(() -> new SagaException(
                        "SAGA_002", "증권 계좌를 찾을 수 없습니다: " + toAccountNumber,
                        org.springframework.http.HttpStatus.BAD_REQUEST));
    }

    private void tryCancelTransfer(Long xUserId, String traceId, Long transferId, Long sagaId) {
        try {
            bankCoreClient.cancelTransfer(xUserId, traceId, transferId);
            log.info("[Saga] bank transfer cancelled: sagaId={}, transferId={}", sagaId, transferId);
        } catch (Exception e) {
            log.warn("[Saga] bank transfer cancel 실패 (무시): sagaId={}, transferId={}, error={}",
                    sagaId, transferId, e.getMessage());
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
