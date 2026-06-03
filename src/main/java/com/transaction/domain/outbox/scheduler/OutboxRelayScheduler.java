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

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int MAX_RETRY = 3;
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterService deadLetterService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByPublishedYnFalseOrderByCreatedAt(PageRequest.of(0, BATCH_SIZE));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("[OutboxRelayScheduler] 발행 대상 이벤트: {}건", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopicName(), event.getPartitionKey(), event.getPayload()).get();
            event.markPublished();
            outboxEventRepository.save(event);
            log.info("[OutboxRelayScheduler] Kafka 발행 완료: outboxId={}, topic={}",
                    event.getOutboxId(), event.getTopicName());

        } catch (Exception e) {
            event.incrementRetry();
            outboxEventRepository.save(event);
            log.warn("[OutboxRelayScheduler] Kafka 발행 실패: outboxId={}, retryCount={}, error={}",
                    event.getOutboxId(), event.getRetryCount(), e.getMessage());

            if (event.getRetryCount() >= MAX_RETRY) {
                moveToDeadLetter(event, e.getMessage());
            }
        }
    }

    private void moveToDeadLetter(OutboxEvent event, String errorMessage) {
        try {
            deadLetterService.save(
                    event.getTopicName(), null,
                    event.getPayload(), errorMessage, null,
                    event.getRetryCount());
            event.markPublished(); // 재시도 중단
            outboxEventRepository.save(event);
            log.error("[OutboxRelayScheduler] DLQ 이동: outboxId={}, topic={}",
                    event.getOutboxId(), event.getTopicName());
        } catch (Exception dlqEx) {
            log.error("[OutboxRelayScheduler] DLQ 저장 실패: outboxId={}, error={}",
                    event.getOutboxId(), dlqEx.getMessage());
        }
    }
}
