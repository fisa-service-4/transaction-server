package com.transaction.domain.saga.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.domain.saga.entity.SagaTransaction;
import com.transaction.domain.saga.enums.SagaType;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.request.StockCashRequest;
import com.transaction.domain.stock.dto.response.StockCashResponse;
import com.transaction.global.exception.SagaException;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.service.AuditService;
import com.transaction.global.service.IdempotencyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorBankToStockTest {

  @Mock private SagaStateManager sagaStateManager;
  @Mock private IdempotencyService idempotencyService;
  @Mock private AuditService auditService;
  @Mock private BankCoreClient bankCoreClient;
  @Mock private StockCoreClient stockCoreClient;

  private SagaOrchestrator sagaOrchestrator;

  private static final Long SAGA_ID = 1L;
  private static final Long TRANSFER_ID = 5001L;
  private static final Long X_USER_ID = 100L;
  private static final String TRACE_ID = "trace-001";
  private static final String IDEM_KEY = "idem-key-001";
  private static final String TO_ACCOUNT_NUMBER = "300-777-000071";

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    sagaOrchestrator =
        new SagaOrchestrator(
            sagaStateManager,
            idempotencyService,
            auditService,
            bankCoreClient,
            stockCoreClient,
            objectMapper);
  }

  private BaasTransferRequest buildRequest() {
    return BaasTransferRequest.builder()
        .fromAccountId(1001L)
        .toBankCode("243")
        .toAccountNumber(TO_ACCOUNT_NUMBER)
        .transferAmount(BigDecimal.valueOf(500000))
        .requestedBy("USER")
        .build();
  }

  /** mockSaga()는 반드시 when() 체인 바깥에서 먼저 호출한 뒤 변수에 담아 사용해야 한다. */
  private SagaTransaction newMockSaga() {
    SagaTransaction saga = mock(SagaTransaction.class);
    when(saga.getSagaId()).thenReturn(SAGA_ID);
    return saga;
  }

  private ApiResponse<BaasTransferCreateResponse> createTransferResponse() {
    return ApiResponse.success(
        new BaasTransferCreateResponse(TRANSFER_ID, "REQUESTED", LocalDateTime.now()), TRACE_ID);
  }

  private ApiResponse<BaasTransferDetailResponse> transferDetailResponse(String status) {
    BaasTransferDetailResponse detail = mock(BaasTransferDetailResponse.class);
    when(detail.getTransferStatus()).thenReturn(status);
    return ApiResponse.success(detail, TRACE_ID);
  }

  private ApiResponse<StockCashResponse> cashResponse() {
    return ApiResponse.success(mock(StockCashResponse.class), TRACE_ID);
  }

  @Test
  void bankToStock_정상흐름_SUCCESS() {
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(eq(SagaType.BANK_TO_STOCK), anyString(), anyString()))
        .thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());

    BaasTransferCreateResponse result =
        sagaOrchestrator.bankToStock(
            buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER);

    assertThat(result.getTransferStatus()).isEqualTo("SUCCESS");
    verify(sagaStateManager).completeSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
    verify(auditService)
        .record(eq("BANK_TO_STOCK"), anyString(), eq("SUCCESS"), any(), any(), any(), any());
  }

  @Test
  void bankToStock_STEP1실패_FAILED_bank_cancel미호출() {
    // transferId=null 이므로 tryCancelTransfer가 early-return → cancelTransfer 미호출
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("bank 연결 실패"));

    assertThatThrownBy(
            () ->
                sagaOrchestrator.bankToStock(
                    buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER))
        .isInstanceOf(SagaException.class);

    verify(sagaStateManager).failSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
    verify(bankCoreClient, never()).cancelTransfer(anyLong(), anyString(), anyLong());
  }

  @Test
  void bankToStock_STEP2실패_CaseA_FAILED() {
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());
    when(stockCoreClient.depositCash(
            anyLong(), anyString(), anyString(), any(StockCashRequest.class)))
        .thenThrow(new RuntimeException("증권 연결 실패"));

    assertThatThrownBy(
            () ->
                sagaOrchestrator.bankToStock(
                    buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER))
        .isInstanceOf(SagaException.class);

    verify(sagaStateManager).failSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
    verify(bankCoreClient).cancelTransfer(eq(X_USER_ID), eq(TRACE_ID), eq(TRANSFER_ID));
  }

  @Test
  void bankToStock_STEP3실패후bank상태SUCCESS_복구() {
    // approve 예외 발생 → bank 상태 조회 → SUCCESS → Saga SUCCESS로 복구
    SagaTransaction saga = newMockSaga();
    ApiResponse<BaasTransferDetailResponse> detailResp = transferDetailResponse("SUCCESS");
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.approveTransfer(anyLong(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("approve timeout"));
    when(bankCoreClient.getTransfer(anyLong(), anyString(), anyLong())).thenReturn(detailResp);

    BaasTransferCreateResponse result =
        sagaOrchestrator.bankToStock(
            buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER);

    assertThat(result.getTransferStatus()).isEqualTo("SUCCESS");
    verify(sagaStateManager).completeSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
    verify(stockCoreClient, never()).withdrawCash(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void bankToStock_STEP3실패후bank상태REQUESTED_CaseB_COMPENSATED() {
    // approve 예외 → bank 상태 REQUESTED → stock withdraw 보상 트랜잭션
    SagaTransaction saga = newMockSaga();
    ApiResponse<BaasTransferDetailResponse> detailResp = transferDetailResponse("REQUESTED");
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.approveTransfer(anyLong(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("approve 실패"));
    when(bankCoreClient.getTransfer(anyLong(), anyString(), anyLong())).thenReturn(detailResp);
    when(stockCoreClient.withdrawCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());

    assertThatThrownBy(
            () ->
                sagaOrchestrator.bankToStock(
                    buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER))
        .isInstanceOf(SagaException.class);

    verify(stockCoreClient).withdrawCash(anyLong(), anyString(), anyString(), any());
    verify(sagaStateManager).compensatedSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString());
  }

  @Test
  void bankToStock_STEP3실패후상태조회도실패_UNKNOWN() {
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.approveTransfer(anyLong(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("approve timeout"));
    when(bankCoreClient.getTransfer(anyLong(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("bank 조회 실패"));

    assertThatThrownBy(
            () ->
                sagaOrchestrator.bankToStock(
                    buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER))
        .isInstanceOf(SagaException.class);

    verify(sagaStateManager).unknownSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
  }

  @Test
  void bankToStock_보상실패_COMPENSATION_FAILED() {
    // Case B + stock withdraw도 예외 → COMPENSATION_FAILED
    SagaTransaction saga = newMockSaga();
    ApiResponse<BaasTransferDetailResponse> detailResp = transferDetailResponse("REQUESTED");
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.approveTransfer(anyLong(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("approve 실패"));
    when(bankCoreClient.getTransfer(anyLong(), anyString(), anyLong())).thenReturn(detailResp);
    when(stockCoreClient.withdrawCash(anyLong(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("보상 withdraw 실패"));

    assertThatThrownBy(
            () ->
                sagaOrchestrator.bankToStock(
                    buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER))
        .isInstanceOf(SagaException.class);

    verify(sagaStateManager).compensationFailedSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString());
  }

  @Test
  void bankToStock_중복요청_캐시응답반환() {
    String cached =
        "{\"transferId\":5001,\"transferStatus\":\"SUCCESS\",\"requestedAt\":\"2026-06-09T10:00:00\"}";
    when(idempotencyService.check(anyString(), anyString(), eq("BANK_TO_STOCK")))
        .thenReturn(Optional.of(cached));

    BaasTransferCreateResponse result =
        sagaOrchestrator.bankToStock(
            buildRequest(), IDEM_KEY, X_USER_ID, TRACE_ID, TO_ACCOUNT_NUMBER);

    assertThat(result.getTransferId()).isEqualTo(5001L);
    verify(bankCoreClient, never()).createTransfer(anyLong(), anyString(), anyString(), any());
    verify(sagaStateManager, never()).initSaga(any(), anyString(), anyString());
  }
}
