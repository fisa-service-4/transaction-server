package com.transaction.domain.saga.service;

import com.transaction.domain.outbox.service.OutboxService;
import com.transaction.domain.saga.entity.SagaStepHistory;
import com.transaction.domain.saga.entity.SagaTransaction;
import com.transaction.domain.saga.enums.SagaStatus;
import com.transaction.domain.saga.enums.SagaStepName;
import com.transaction.domain.saga.enums.SagaStepStatus;
import com.transaction.domain.saga.enums.SagaType;
import com.transaction.domain.saga.repository.SagaStepHistoryRepository;
import com.transaction.domain.saga.repository.SagaTransactionRepository;
import com.transaction.global.config.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.global.service.IdempotencyService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SagaStateManager {

  private final SagaTransactionRepository sagaTransactionRepository;
  private final SagaStepHistoryRepository sagaStepHistoryRepository;
  private final OutboxService outboxService;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public SagaTransaction initSaga(SagaType type, String transactionKey, String outboxPayload) {
    SagaTransaction saga = SagaTransaction.create(type, transactionKey);
    saga = sagaTransactionRepository.save(saga);
    outboxService.save(
        "SAGA",
        saga.getSagaId(),
        "saga.started",
        KafkaTopics.SAGA_STARTED,
        String.valueOf(saga.getSagaId()),
        outboxPayload);
    return saga;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordStep(
      Long sagaId,
      SagaStepName name,
      int order,
      SagaStepStatus status,
      String requestPayload,
      String responsePayload,
      String errorMessage) {
    sagaStepHistoryRepository.save(
        SagaStepHistory.create(
            sagaId, name, order, status, requestPayload, responsePayload, errorMessage));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeSaga(
      Long sagaId, String idempotencyKey, String responsePayload, String outboxPayload) {
    SagaTransaction saga = findSaga(sagaId);
    saga.updateStatus(SagaStatus.SUCCESS);
    sagaTransactionRepository.save(saga);
    outboxService.save(
        "SAGA",
        sagaId,
        "saga.completed",
        KafkaTopics.SAGA_COMPLETED,
        String.valueOf(sagaId),
        outboxPayload);
    idempotencyService.complete(idempotencyKey, responsePayload);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failSaga(Long sagaId, String idempotencyKey, String reason, String outboxPayload) {
    SagaTransaction saga = findSaga(sagaId);
    saga.fail(SagaStatus.FAILED, reason);
    sagaTransactionRepository.save(saga);
    outboxService.save(
        "SAGA",
        sagaId,
        "saga.failed",
        KafkaTopics.SAGA_FAILED,
        String.valueOf(sagaId),
        outboxPayload);
    idempotencyService.fail(idempotencyKey);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void startCompensation(Long sagaId, String outboxPayload) {
    SagaTransaction saga = findSaga(sagaId);
    saga.updateStatus(SagaStatus.COMPENSATING);
    sagaTransactionRepository.save(saga);
    outboxService.save(
        "SAGA",
        sagaId,
        "saga.compensated",
        KafkaTopics.SAGA_COMPENSATED,
        String.valueOf(sagaId),
        outboxPayload);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void compensatedSaga(Long sagaId, String idempotencyKey, String outboxPayload) {
    SagaTransaction saga = findSaga(sagaId);
    saga.updateStatus(SagaStatus.COMPENSATED);
    sagaTransactionRepository.save(saga);
    outboxService.save(
        "SAGA",
        sagaId,
        "saga.compensated",
        KafkaTopics.SAGA_COMPENSATED,
        String.valueOf(sagaId),
        outboxPayload);
    idempotencyService.fail(idempotencyKey);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void compensationFailedSaga(Long sagaId, String idempotencyKey, String reason) {
    SagaTransaction saga = findSaga(sagaId);
    saga.fail(SagaStatus.COMPENSATION_FAILED, reason);
    sagaTransactionRepository.save(saga);
    try {
      outboxService.save(
          "SAGA",
          sagaId,
          "saga.compensation_failed",
          KafkaTopics.SAGA_COMPENSATION_FAILED,
          String.valueOf(sagaId),
          objectMapper.writeValueAsString(Map.of("sagaId", sagaId, "reason", reason)));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("compensation_failed payload 직렬화 실패", e);
    }
    idempotencyService.fail(idempotencyKey);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void unknownSaga(Long sagaId, String idempotencyKey, String reason, String outboxPayload) {
    SagaTransaction saga = findSaga(sagaId);
    saga.fail(SagaStatus.UNKNOWN, reason);
    sagaTransactionRepository.save(saga);
    outboxService.save(
        "SAGA", sagaId, "saga.unknown", "saga.unknown", String.valueOf(sagaId), outboxPayload);
    idempotencyService.fail(idempotencyKey);
  }

  private SagaTransaction findSaga(Long sagaId) {
    return sagaTransactionRepository
        .findById(sagaId)
        .orElseThrow(() -> new IllegalStateException("Saga를 찾을 수 없습니다: " + sagaId));
  }
}
