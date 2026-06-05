package com.transaction.domain.outbox.service;

import com.transaction.domain.outbox.entity.OutboxEvent;
import com.transaction.domain.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

  private final OutboxEventRepository outboxEventRepository;

  @Transactional
  public void save(
      String aggregateType, Long aggregateId, String eventType, String topicName, String payload) {
    save(aggregateType, aggregateId, eventType, topicName, String.valueOf(aggregateId), payload);
  }

  @Transactional
  public void save(
      String aggregateType,
      Long aggregateId,
      String eventType,
      String topicName,
      String partitionKey,
      String payload) {
    OutboxEvent event =
        OutboxEvent.create(aggregateType, aggregateId, eventType, topicName, partitionKey, payload);
    outboxEventRepository.save(event);
    log.info(
        "[OutboxService] Outbox 이벤트 저장: topic={}, eventType={}, aggregateId={}",
        topicName,
        eventType,
        aggregateId);
  }
}
