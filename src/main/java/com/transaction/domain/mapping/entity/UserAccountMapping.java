package com.transaction.domain.mapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_account_mapping")
@Getter
@NoArgsConstructor
public class UserAccountMapping {

  @EmbeddedId private UserAccountMappingId id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "x_user_id", nullable = false)
  private Long xUserId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
