package com.transaction.domain.reconciliation.service;

import com.transaction.domain.reconciliation.entity.ReconciliationResult;
import com.transaction.domain.reconciliation.repository.ReconciliationResultRepository;
import com.transaction.domain.saga.enums.SagaStatus;
import com.transaction.domain.saga.repository.SagaTransactionRepository;
import com.transaction.global.config.ReconciliationProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

  private final SagaTransactionRepository sagaTransactionRepository;
  private final ReconciliationResultRepository reconciliationResultRepository;
  private final ReconciliationProperties properties;

  @Transactional
  public void check() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime processingThreshold = now.minusMinutes(properties.getProcessingTimeoutMinutes());
    LocalDateTime compensatingThreshold =
        now.minusMinutes(properties.getCompensatingTimeoutMinutes());

    long compensationFailed = sagaTransactionRepository.countBySagaStatus(SagaStatus.COMPENSATION_FAILED);
    long unknown = sagaTransactionRepository.countBySagaStatus(SagaStatus.UNKNOWN);
    long processingStuck = sagaTransactionRepository.countBySagaStatusAndStartedAtBefore(
        SagaStatus.PROCESSING, processingThreshold);
    long compensatingStuck = sagaTransactionRepository.countBySagaStatusAndStartedAtBefore(
        SagaStatus.COMPENSATING, compensatingThreshold);

    long totalAnomalies = compensationFailed + unknown + processingStuck + compensatingStuck;
    String status = totalAnomalies == 0 ? "SUCCESS" : "FAILED";

    ReconciliationResult result = ReconciliationResult.create(
        "SAGA_ANOMALY",
        "transaction-server",
        "saga_transaction",
        (long) sagaTransactionRepository.count(),
        null,
        totalAnomalies,
        status);
    reconciliationResultRepository.save(result);

    log.info(
        "[Reconciliation] 검증 완료: status={}, compensationFailed={}, unknown={}, processingStuck={}, compensatingStuck={}",
        status, compensationFailed, unknown, processingStuck, compensatingStuck);

    if (totalAnomalies > 0) {
      log.warn("[Reconciliation] Saga 이상 상태 감지: 총 {}건 수동 확인 필요", totalAnomalies);
    }
  }
}
