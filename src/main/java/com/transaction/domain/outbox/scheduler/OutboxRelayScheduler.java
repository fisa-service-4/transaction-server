package com.transaction.domain.outbox.scheduler;

import com.transaction.domain.deadletter.service.DeadLetterService;
import com.transaction.domain.outbox.entity.OutboxEvent;
import com.transaction.domain.outbox.service.OutboxService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

  private static final int BATCH_SIZE = 20;
  private static final int MAX_RETRY = 3;

  private final OutboxService outboxService;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final DeadLetterService deadLetterService;

  @Scheduled(fixedDelay = 5000)
  public void relay() {
    List<OutboxEvent> events = outboxService.findPending(BATCH_SIZE);

    for (OutboxEvent event : events) {
      try {
        kafkaTemplate.send(event.getTopicName(), event.getPartitionKey(), event.getPayload()).get();
        outboxService.markPublished(event.getOutboxId());
        log.info(
            "[OutboxRelay] 발행 완료: topic={}, outboxId={}",
            event.getTopicName(),
            event.getOutboxId());
      } catch (Exception e) {
        int retryCount = outboxService.incrementRetry(event.getOutboxId());
        log.warn(
            "[OutboxRelay] 발행 실패: topic={}, outboxId={}, retryCount={}",
            event.getTopicName(),
            event.getOutboxId(),
            retryCount);

        if (retryCount >= MAX_RETRY) {
          deadLetterService.save(
              event.getTopicName(),
              "outbox-relay",
              event.getPayload(),
              e.getMessage(),
              null,
              retryCount);
          outboxService.markPublished(event.getOutboxId());
          log.error(
              "[OutboxRelay] DLQ 이관: topic={}, outboxId={}",
              event.getTopicName(),
              event.getOutboxId());
        }
      }
    }
  }
}
