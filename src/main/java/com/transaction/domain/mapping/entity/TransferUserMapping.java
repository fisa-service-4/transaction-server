package com.transaction.domain.mapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "transfer_user_mapping")
@Getter
@NoArgsConstructor
public class TransferUserMapping {

  @Id
  @Column(name = "transfer_id")
  private Long transferId;

  @Column(name = "x_user_id", nullable = false)
  private Long xUserId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public TransferUserMapping(Long transferId, Long xUserId) {
    this.transferId = transferId;
    this.xUserId = xUserId;
  }
}
