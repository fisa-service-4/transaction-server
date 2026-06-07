package com.transaction.domain.outbox.scheduler;

import com.transaction.domain.deadletter.service.DeadLetterService;
import com.transaction.domain.outbox.entity.OutboxEvent;
import com.transaction.domain.outbox.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

  private static final int BATCH_SIZE = 20;
  private static final int MAX_RETRY = 3;

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final DeadLetterService deadLetterService;

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void relay() {
    List<OutboxEvent> events =
        outboxEventRepository.findByPublishedYnFalseOrderByCreatedAt(
            PageRequest.of(0, BATCH_SIZE));

    for (OutboxEvent event : events) {
      try {
        kafkaTemplate.send(event.getTopicName(), event.getPartitionKey(), event.getPayload()).get();
        event.markPublished();
        log.info(
            "[OutboxRelay] 발행 완료: topic={}, outboxId={}", event.getTopicName(), event.getOutboxId());
      } catch (Exception e) {
        event.incrementRetry();
        log.warn(
            "[OutboxRelay] 발행 실패: topic={}, outboxId={}, retryCount={}",
            event.getTopicName(),
            event.getOutboxId(),
            event.getRetryCount());

        if (event.getRetryCount() >= MAX_RETRY) {
          deadLetterService.save(
              event.getTopicName(),
              "outbox-relay",
              event.getPayload(),
              e.getMessage(),
              null,
              event.getRetryCount());
          event.markPublished(); // DLQ 이관 후 재처리 방지
          log.error(
              "[OutboxRelay] DLQ 이관: topic={}, outboxId={}", event.getTopicName(), event.getOutboxId());
        }
      }
    }
  }
}
