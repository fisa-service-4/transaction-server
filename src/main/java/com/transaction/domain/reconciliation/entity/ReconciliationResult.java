package com.transaction.domain.reconciliation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reconciliation_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "reconciliation_id")
  private Long reconciliationId;

  @Column(name = "reconciliation_type", nullable = false, length = 100)
  private String reconciliationType;

  @Column(name = "source_system", nullable = false, length = 100)
  private String sourceSystem;

  @Column(name = "target_system", nullable = false, length = 100)
  private String targetSystem;

  @Column(name = "source_count")
  private Long sourceCount;

  @Column(name = "target_count")
  private Long targetCount;

  @Column(name = "mismatch_count")
  private Long mismatchCount;

  @Column(name = "compensation_failed_count", nullable = false)
  private Long compensationFailedCount;

  @Column(name = "unknown_count", nullable = false)
  private Long unknownCount;

  @Column(name = "processing_stuck_count", nullable = false)
  private Long processingStuckCount;

  @Column(name = "compensating_stuck_count", nullable = false)
  private Long compensatingStuckCount;

  @Column(name = "reconciliation_status", nullable = false, length = 30)
  private String reconciliationStatus;

  @Column(name = "checked_at", nullable = false)
  private LocalDateTime checkedAt;

  public static ReconciliationResult create(
      String type,
      String sourceSystem,
      String targetSystem,
      Long sourceCount,
      Long targetCount,
      Long compensationFailedCount,
      Long unknownCount,
      Long processingStuckCount,
      Long compensatingStuckCount,
      String status) {
    ReconciliationResult result = new ReconciliationResult();
    result.reconciliationType = type;
    result.sourceSystem = sourceSystem;
    result.targetSystem = targetSystem;
    result.sourceCount = sourceCount;
    result.targetCount = targetCount;
    result.compensationFailedCount = compensationFailedCount;
    result.unknownCount = unknownCount;
    result.processingStuckCount = processingStuckCount;
    result.compensatingStuckCount = compensatingStuckCount;
    result.mismatchCount =
        compensationFailedCount + unknownCount + processingStuckCount + compensatingStuckCount;
    result.reconciliationStatus = status;
    result.checkedAt = LocalDateTime.now();
    return result;
  }
}
