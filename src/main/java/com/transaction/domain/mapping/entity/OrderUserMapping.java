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
@Table(name = "order_user_mapping")
@Getter
@NoArgsConstructor
public class OrderUserMapping {

  @Id
  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "x_user_id", nullable = false)
  private Long xUserId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public OrderUserMapping(Long orderId, Long xUserId) {
    this.orderId = orderId;
    this.xUserId = xUserId;
  }
}
