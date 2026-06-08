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
class SagaOrchestratorStockToBankTest {

  @Mock private SagaStateManager sagaStateManager;
  @Mock private IdempotencyService idempotencyService;
  @Mock private AuditService auditService;
  @Mock private BankCoreClient bankCoreClient;
  @Mock private StockCoreClient stockCoreClient;

  private SagaOrchestrator sagaOrchestrator;

  private static final Long SAGA_ID = 2L;
  private static final Long TRANSFER_ID = 6001L;
  private static final Long X_USER_ID = 200L;
  private static final Long SETTLEMENT_X_USER_ID = 201L;
  private static final Long SETTLEMENT_ACCOUNT_ID = 9001L;
  private static final String TRACE_ID = "trace-stb-001";
  private static final String IDEM_KEY = "idem-stb-001";
  private static final String FROM_ACCOUNT_NUMBER = "300-777-000071";

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
        .fromAccountId(2001L)
        .toBankCode("088")
        .toAccountNumber("110-123-456789")
        .transferAmount(BigDecimal.valueOf(300000))
        .requestedBy("USER")
        .build();
  }

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
  void stockToBank_정상흐름_SUCCESS() {
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("STOCK_TO_BANK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(eq(SagaType.STOCK_TO_BANK), anyString(), anyString()))
        .thenReturn(saga);
    when(stockCoreClient.withdrawCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(createTransferResponse());

    BaasTransferCreateResponse result =
        sagaOrchestrator.stockToBank(
            buildRequest(),
            IDEM_KEY,
            X_USER_ID,
            TRACE_ID,
            FROM_ACCOUNT_NUMBER,
            SETTLEMENT_ACCOUNT_ID,
            SETTLEMENT_X_USER_ID);

    assertThat(result.getTransferStatus()).isEqualTo("SUCCESS");
    verify(sagaStateManager).completeSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
    verify(auditService)
        .record(eq("STOCK_TO_BANK"), anyString(), eq("SUCCESS"), any(), any(), any(), any());
  }

  @Test
  void stockToBank_STEP1실패_FAILED_보상없음() {
    // stock withdraw 실패 → bank 미호출 → 보상 없이 FAILED 종료
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("STOCK_TO_BANK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(stockCoreClient.withdrawCash(anyLong(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("예수금 부족"));

    assertThatThrownBy(
            () ->
                sagaOrchestrator.stockToBank(
                    buildRequest(),
                    IDEM_KEY,
                    X_USER_ID,
                    TRACE_ID,
                    FROM_ACCOUNT_NUMBER,
                    SETTLEMENT_ACCOUNT_ID,
                    SETTLEMENT_X_USER_ID))
        .isInstanceOf(SagaException.class);

    verify(sagaStateManager).failSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString(), anyString());
    verify(bankCoreClient, never()).createTransfer(anyLong(), anyString(), anyString(), any());
    verify(stockCoreClient, never()).depositCash(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void stockToBank_STEP2실패_보상성공_COMPENSATED() {
    // bank createTransfer 실패 → stock deposit 보상 성공 → COMPENSATED
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("STOCK_TO_BANK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(stockCoreClient.withdrawCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("bank 서버 오류"));
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());

    assertThatThrownBy(
            () ->
                sagaOrchestrator.stockToBank(
                    buildRequest(),
                    IDEM_KEY,
                    X_USER_ID,
                    TRACE_ID,
                    FROM_ACCOUNT_NUMBER,
                    SETTLEMENT_ACCOUNT_ID,
                    SETTLEMENT_X_USER_ID))
        .isInstanceOf(SagaException.class);

    verify(stockCoreClient).depositCash(anyLong(), anyString(), anyString(), any());
    verify(sagaStateManager).compensatedSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString());
  }

  @Test
  void stockToBank_보상실패_COMPENSATION_FAILED() {
    // bank 실패 + stock deposit 보상도 예외 → COMPENSATION_FAILED
    SagaTransaction saga = newMockSaga();
    when(idempotencyService.check(anyString(), anyString(), eq("STOCK_TO_BANK")))
        .thenReturn(Optional.empty());
    when(sagaStateManager.initSaga(any(), anyString(), anyString())).thenReturn(saga);
    when(stockCoreClient.withdrawCash(anyLong(), anyString(), anyString(), any()))
        .thenReturn(cashResponse());
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("bank 서버 오류"));
    when(stockCoreClient.depositCash(anyLong(), anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("보상 deposit 실패"));

    assertThatThrownBy(
            () ->
                sagaOrchestrator.stockToBank(
                    buildRequest(),
                    IDEM_KEY,
                    X_USER_ID,
                    TRACE_ID,
                    FROM_ACCOUNT_NUMBER,
                    SETTLEMENT_ACCOUNT_ID,
                    SETTLEMENT_X_USER_ID))
        .isInstanceOf(SagaException.class);

    verify(sagaStateManager).compensationFailedSaga(eq(SAGA_ID), eq(IDEM_KEY), anyString());
  }
}
