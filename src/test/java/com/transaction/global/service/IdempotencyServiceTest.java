package com.transaction.global.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transaction.global.entity.IdempotencyKey;
import com.transaction.global.exception.DuplicateRequestInProgressException;
import com.transaction.global.repository.IdempotencyKeyRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

  @Mock private IdempotencyKeyRepository idempotencyKeyRepository;

  private IdempotencyService idempotencyService;

  @BeforeEach
  void setUp() {
    idempotencyService = new IdempotencyService(idempotencyKeyRepository);
  }

  @Test
  void check_신규키_빈값반환및PROCESSING저장() {
    when(idempotencyKeyRepository.findById("key-1")).thenReturn(Optional.empty());

    Optional<String> result = idempotencyService.check("key-1", "hash", "BANK_TO_STOCK");

    assertThat(result).isEmpty();
    verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
  }

  @Test
  void check_PROCESSING키_예외발생() {
    IdempotencyKey processingKey = IdempotencyKey.create("key-2", "hash", "BANK_TO_STOCK");
    when(idempotencyKeyRepository.findById("key-2")).thenReturn(Optional.of(processingKey));

    assertThatThrownBy(() -> idempotencyService.check("key-2", "hash", "BANK_TO_STOCK"))
        .isInstanceOf(DuplicateRequestInProgressException.class);
  }

  @Test
  void check_SUCCESS키_캐시응답반환() {
    IdempotencyKey successKey = IdempotencyKey.create("key-3", "hash", "BANK_TO_STOCK");
    successKey.complete("{\"transferId\":5001}");
    when(idempotencyKeyRepository.findById("key-3")).thenReturn(Optional.of(successKey));

    Optional<String> result = idempotencyService.check("key-3", "hash", "BANK_TO_STOCK");

    assertThat(result).isPresent();
    assertThat(result.get()).contains("transferId");
    verify(idempotencyKeyRepository, never()).save(any());
  }

  @Test
  void shouldAllowRetryWhenPreviousRequestFailed() {
    // FAILED 상태 키는 재시도를 허용 — isProcessing()=false, getResponsePayload()=null
    // check()는 Optional.empty()를 반환하여 호출자가 신규 요청으로 처리하게 함
    // 단, save()는 호출되지 않음 (check 내부 분기에서 early return)
    IdempotencyKey failedKey = IdempotencyKey.create("key-4", "hash", "BANK_TO_STOCK");
    failedKey.fail();
    when(idempotencyKeyRepository.findById("key-4")).thenReturn(Optional.of(failedKey));

    Optional<String> result = idempotencyService.check("key-4", "hash", "BANK_TO_STOCK");

    assertThat(result).isEmpty();
    verify(idempotencyKeyRepository, never()).save(any());
  }

  @Test
  void complete_SUCCESS상태로전이및응답저장() {
    IdempotencyKey processingKey = IdempotencyKey.create("key-5", "hash", "BANK_TO_STOCK");
    when(idempotencyKeyRepository.findById("key-5")).thenReturn(Optional.of(processingKey));

    idempotencyService.complete("key-5", "{\"transferId\":5001}");

    assertThat(processingKey.isCompleted()).isTrue();
    assertThat(processingKey.getResponsePayload()).contains("transferId");
    verify(idempotencyKeyRepository).save(processingKey);
  }

  @Test
  void fail_FAILED상태로전이() {
    IdempotencyKey processingKey = IdempotencyKey.create("key-6", "hash", "BANK_TO_STOCK");
    when(idempotencyKeyRepository.findById("key-6")).thenReturn(Optional.of(processingKey));

    idempotencyService.fail("key-6");

    assertThat(processingKey.isProcessing()).isFalse();
    assertThat(processingKey.isCompleted()).isFalse();
    verify(idempotencyKeyRepository).save(processingKey);
  }
}
