package com.transaction.domain.outbox.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transaction.domain.deadletter.service.DeadLetterService;
import com.transaction.domain.outbox.entity.OutboxEvent;
import com.transaction.domain.outbox.service.OutboxService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

  @Mock private OutboxService outboxService;

  @SuppressWarnings("unchecked")
  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  @Mock private DeadLetterService deadLetterService;

  private OutboxRelayScheduler scheduler;

  private static final Long OUTBOX_ID = 1L;
  private static final String TOPIC = "transfer.requested";
  private static final String PARTITION_KEY = "pk-001";
  private static final String PAYLOAD = "{\"transferId\":5001}";

  @BeforeEach
  void setUp() {
    scheduler = new OutboxRelayScheduler(outboxService, kafkaTemplate, deadLetterService);
  }

  private OutboxEvent mockEvent() {
    OutboxEvent event = mock(OutboxEvent.class);
    when(event.getOutboxId()).thenReturn(OUTBOX_ID);
    when(event.getTopicName()).thenReturn(TOPIC);
    when(event.getPartitionKey()).thenReturn(PARTITION_KEY);
    when(event.getPayload()).thenReturn(PAYLOAD);
    return event;
  }

  @SuppressWarnings("unchecked")
  private void stubKafkaSuccess() throws Exception {
    CompletableFuture<SendResult<String, String>> future =
        CompletableFuture.completedFuture(mock(SendResult.class));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
  }

  @SuppressWarnings("unchecked")
  private void stubKafkaFail() throws Exception {
    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("Kafka 연결 실패"));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
  }

  @Test
  void relay_pending없음_아무동작안함() {
    when(outboxService.findPending(anyInt())).thenReturn(List.of());

    scheduler.relay();

    verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    verify(outboxService, never()).markPublished(anyLong());
  }

  @Test
  void relay_발행성공_markPublished호출() throws Exception {
    OutboxEvent event = mockEvent();
    when(outboxService.findPending(anyInt())).thenReturn(List.of(event));
    stubKafkaSuccess();

    scheduler.relay();

    verify(outboxService).markPublished(OUTBOX_ID);
    verify(deadLetterService, never()).save(any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  void relay_1회실패_DLQ미이관() throws Exception {
    OutboxEvent event = mockEvent();
    when(outboxService.findPending(anyInt())).thenReturn(List.of(event));
    stubKafkaFail();
    when(outboxService.incrementRetry(OUTBOX_ID)).thenReturn(1);

    scheduler.relay();

    verify(outboxService).incrementRetry(OUTBOX_ID);
    verify(deadLetterService, never()).save(any(), any(), any(), any(), any(), anyInt());
    verify(outboxService, never()).markPublished(anyLong());
  }

  @Test
  void relay_3회실패_DLQ이관() throws Exception {
    OutboxEvent event = mockEvent();
    when(outboxService.findPending(anyInt())).thenReturn(List.of(event));
    stubKafkaFail();
    when(outboxService.incrementRetry(OUTBOX_ID)).thenReturn(3);

    scheduler.relay();

    verify(deadLetterService)
        .save(eq(TOPIC), eq("outbox-relay"), eq(PAYLOAD), anyString(), isNull(), eq(3));
    verify(outboxService).markPublished(OUTBOX_ID);
  }
}
