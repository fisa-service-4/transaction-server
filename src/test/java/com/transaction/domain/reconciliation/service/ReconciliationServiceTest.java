package com.transaction.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transaction.domain.reconciliation.entity.ReconciliationResult;
import com.transaction.domain.reconciliation.repository.ReconciliationResultRepository;
import com.transaction.domain.saga.enums.SagaStatus;
import com.transaction.domain.saga.repository.SagaTransactionRepository;
import com.transaction.global.config.ReconciliationProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

  @Mock private SagaTransactionRepository sagaTransactionRepository;
  @Mock private ReconciliationResultRepository reconciliationResultRepository;

  private ReconciliationService reconciliationService;

  @BeforeEach
  void setUp() {
    ReconciliationProperties properties = new ReconciliationProperties();
    reconciliationService =
        new ReconciliationService(
            sagaTransactionRepository, reconciliationResultRepository, properties);
  }

  private void stubCounts(long compensationFailed, long unknown, long stuck, long compensating) {
    when(sagaTransactionRepository.countBySagaStatus(SagaStatus.COMPENSATION_FAILED))
        .thenReturn(compensationFailed);
    when(sagaTransactionRepository.countBySagaStatus(SagaStatus.UNKNOWN)).thenReturn(unknown);
    when(sagaTransactionRepository.countBySagaStatusAndStartedAtBefore(
            eq(SagaStatus.PROCESSING), any(LocalDateTime.class)))
        .thenReturn(stuck);
    when(sagaTransactionRepository.countBySagaStatusAndStartedAtBefore(
            eq(SagaStatus.COMPENSATING), any(LocalDateTime.class)))
        .thenReturn(compensating);
    when(sagaTransactionRepository.count()).thenReturn(10L);
  }

  @Test
  void check_이상없음_SUCCESS() {
    stubCounts(0, 0, 0, 0);

    reconciliationService.check();

    ArgumentCaptor<ReconciliationResult> captor =
        ArgumentCaptor.forClass(ReconciliationResult.class);
    verify(reconciliationResultRepository).save(captor.capture());
    ReconciliationResult saved = captor.getValue();

    assertThat(saved.getReconciliationStatus()).isEqualTo("SUCCESS");
    assertThat(saved.getMismatchCount()).isEqualTo(0L);
  }

  @Test
  void check_COMPENSATION_FAILED존재_카운트분리() {
    stubCounts(2, 0, 0, 0);

    reconciliationService.check();

    ArgumentCaptor<ReconciliationResult> captor =
        ArgumentCaptor.forClass(ReconciliationResult.class);
    verify(reconciliationResultRepository).save(captor.capture());
    ReconciliationResult saved = captor.getValue();

    assertThat(saved.getReconciliationStatus()).isEqualTo("FAILED");
    assertThat(saved.getMismatchCount()).isEqualTo(2L);
    assertThat(saved.getCompensationFailedCount()).isEqualTo(2L);
    assertThat(saved.getUnknownCount()).isEqualTo(0L);
  }

  @Test
  void check_복합이상_각카운트독립집계() {
    // compensationFailed=1, unknown=2, processingStuck=3, compensatingStuck=4 → mismatch=10
    stubCounts(1, 2, 3, 4);

    reconciliationService.check();

    ArgumentCaptor<ReconciliationResult> captor =
        ArgumentCaptor.forClass(ReconciliationResult.class);
    verify(reconciliationResultRepository).save(captor.capture());
    ReconciliationResult saved = captor.getValue();

    assertThat(saved.getReconciliationStatus()).isEqualTo("FAILED");
    assertThat(saved.getMismatchCount()).isEqualTo(10L);
    assertThat(saved.getCompensationFailedCount()).isEqualTo(1L);
    assertThat(saved.getUnknownCount()).isEqualTo(2L);
    assertThat(saved.getProcessingStuckCount()).isEqualTo(3L);
    assertThat(saved.getCompensatingStuckCount()).isEqualTo(4L);
  }
}
