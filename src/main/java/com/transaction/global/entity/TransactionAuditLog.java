package com.transaction.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "transaction_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionAuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "transaction_audit_log_id")
  private Long transactionAuditLogId;

  @Column(name = "transaction_type", nullable = false, length = 100)
  private String transactionType;

  @Column(name = "transaction_key", nullable = false, length = 255)
  private String transactionKey;

  @Column(name = "transaction_status", nullable = false, length = 50)
  private String transactionStatus;

  @Column(name = "source_system", nullable = false, length = 100)
  private String sourceSystem;

  @Column(name = "target_system", length = 100)
  private String targetSystem;

  @Lob
  @Column(name = "request_payload")
  private String requestPayload;

  @Lob
  @Column(name = "response_payload")
  private String responsePayload;

  @Column(name = "audited_at", nullable = false)
  private LocalDateTime auditedAt;

  public static TransactionAuditLog create(
      String transactionType,
      String transactionKey,
      String status,
      String sourceSystem,
      String targetSystem,
      String requestPayload,
      String responsePayload) {
    TransactionAuditLog log = new TransactionAuditLog();
    log.transactionType = transactionType;
    log.transactionKey = transactionKey;
    log.transactionStatus = status;
    log.sourceSystem = sourceSystem;
    log.targetSystem = targetSystem;
    log.requestPayload = requestPayload;
    log.responsePayload = responsePayload;
    log.auditedAt = LocalDateTime.now();
    return log;
  }
}
