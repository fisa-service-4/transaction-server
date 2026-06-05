package com.transaction.domain.saga.entity;

import com.transaction.domain.saga.enums.SagaStepName;
import com.transaction.domain.saga.enums.SagaStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_step_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaStepHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "step_history_id")
  private Long stepHistoryId;

  @Column(name = "saga_id", nullable = false)
  private Long sagaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_name", nullable = false, length = 100)
  private SagaStepName stepName;

  @Column(name = "step_order", nullable = false)
  private Integer stepOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_status", nullable = false, length = 30)
  private SagaStepStatus stepStatus;

  @Lob
  @Column(name = "request_payload")
  private String requestPayload;

  @Lob
  @Column(name = "response_payload")
  private String responsePayload;

  @Lob
  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "processed_at", nullable = false)
  private LocalDateTime processedAt;

  public static SagaStepHistory create(
      Long sagaId,
      SagaStepName stepName,
      int stepOrder,
      SagaStepStatus status,
      String requestPayload,
      String responsePayload,
      String errorMessage) {
    SagaStepHistory history = new SagaStepHistory();
    history.sagaId = sagaId;
    history.stepName = stepName;
    history.stepOrder = stepOrder;
    history.stepStatus = status;
    history.requestPayload = requestPayload;
    history.responsePayload = responsePayload;
    history.errorMessage = errorMessage;
    history.processedAt = LocalDateTime.now();
    return history;
  }
}
