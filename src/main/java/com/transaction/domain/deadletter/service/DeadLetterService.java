package com.transaction.domain.deadletter.service;

import com.transaction.domain.deadletter.entity.DeadLetterEvent;
import com.transaction.domain.deadletter.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

  private final DeadLetterEventRepository deadLetterEventRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(
      String originalTopic,
      String consumerGroup,
      String payload,
      String errorMessage,
      String stackTrace,
      int retryCount) {
    DeadLetterEvent event =
        DeadLetterEvent.create(
            originalTopic, consumerGroup, payload, errorMessage, stackTrace, retryCount);
    deadLetterEventRepository.save(event);
    log.warn("[DeadLetterService] DLQ 저장: topic={}, retryCount={}", originalTopic, retryCount);
  }
}
