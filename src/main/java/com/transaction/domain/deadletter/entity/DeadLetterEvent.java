package com.transaction.domain.deadletter.entity;

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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "dead_letter_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeadLetterEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "dead_letter_id")
  private Long deadLetterId;

  @Column(name = "original_topic", nullable = false, length = 255)
  private String originalTopic;

  @Column(name = "consumer_group", length = 255)
  private String consumerGroup;

  @Lob
  @Column(name = "payload")
  private String payload;

  @Lob
  @Column(name = "error_message")
  private String errorMessage;

  @Lob
  @Column(name = "stack_trace")
  private String stackTrace;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "resolved_yn", nullable = false)
  private boolean resolvedYn;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static DeadLetterEvent create(
      String originalTopic,
      String consumerGroup,
      String payload,
      String errorMessage,
      String stackTrace,
      int retryCount) {
    DeadLetterEvent event = new DeadLetterEvent();
    event.originalTopic = originalTopic;
    event.consumerGroup = consumerGroup;
    event.payload = payload;
    event.errorMessage = errorMessage;
    event.stackTrace = stackTrace;
    event.retryCount = retryCount;
    event.resolvedYn = false;
    return event;
  }
}
