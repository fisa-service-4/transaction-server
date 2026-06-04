package com.transaction.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

  @Id
  @Column(name = "idempotency_key", length = 255)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 255)
  private String requestHash;

  @Column(name = "request_type", nullable = false, length = 100)
  private String requestType;

  @Lob
  @Column(name = "response_payload")
  private String responsePayload;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "expired_at")
  private LocalDateTime expiredAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static IdempotencyKey create(String key, String requestHash, String requestType) {
    IdempotencyKey ik = new IdempotencyKey();
    ik.idempotencyKey = key;
    ik.requestHash = requestHash;
    ik.requestType = requestType;
    ik.status = "PROCESSING";
    ik.expiredAt = LocalDateTime.now().plusHours(24);
    return ik;
  }

  public void complete(String responsePayload) {
    this.status = "SUCCESS";
    this.responsePayload = responsePayload;
  }

  public void fail() {
    this.status = "FAILED";
  }

  public boolean isCompleted() {
    return "SUCCESS".equals(this.status);
  }

  public boolean isProcessing() {
    return "PROCESSING".equals(this.status);
  }
}
