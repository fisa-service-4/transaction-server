package com.transaction.global.service;

import com.transaction.global.entity.IdempotencyKey;
import com.transaction.global.repository.IdempotencyKeyRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional
    public Optional<String> check(String key, String requestHash, String requestType) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(key);
        if (existing.isPresent()) {
            IdempotencyKey ik = existing.get();
            log.info("[IdempotencyService] 중복 요청 감지: key={}, status={}", key, ik.getStatus());
            return Optional.ofNullable(ik.getResponsePayload());
        }
        idempotencyKeyRepository.save(IdempotencyKey.create(key, requestHash, requestType));
        return Optional.empty();
    }

    @Transactional
    public void complete(String key, String responsePayload) {
        idempotencyKeyRepository.findById(key).ifPresent(ik -> {
            ik.complete(responsePayload);
            idempotencyKeyRepository.save(ik);
            log.info("[IdempotencyService] 완료 처리: key={}", key);
        });
    }

    @Transactional
    public void fail(String key) {
        idempotencyKeyRepository.findById(key).ifPresent(ik -> {
            ik.fail();
            idempotencyKeyRepository.save(ik);
            log.warn("[IdempotencyService] 실패 처리: key={}", key);
        });
    }
}
