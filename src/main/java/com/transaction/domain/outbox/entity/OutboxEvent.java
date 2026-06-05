package com.transaction.domain.outbox.entity;

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
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "outbox_id")
  private Long outboxId;

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "topic_name", nullable = false, length = 255)
  private String topicName;

  @Column(name = "partition_key", length = 255)
  private String partitionKey;

  @Lob
  @Column(name = "payload", nullable = false)
  private String payload;

  @Lob
  @Column(name = "headers")
  private String headers;

  @Column(name = "published_yn", nullable = false)
  private boolean publishedYn;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static OutboxEvent create(
      String aggregateType,
      Long aggregateId,
      String eventType,
      String topicName,
      String partitionKey,
      String payload) {
    OutboxEvent event = new OutboxEvent();
    event.aggregateType = aggregateType;
    event.aggregateId = aggregateId;
    event.eventType = eventType;
    event.topicName = topicName;
    event.partitionKey = partitionKey;
    event.payload = payload;
    event.publishedYn = false;
    event.retryCount = 0;
    return event;
  }

  public void markPublished() {
    this.publishedYn = true;
    this.publishedAt = LocalDateTime.now();
  }

  public void incrementRetry() {
    this.retryCount++;
  }
}
