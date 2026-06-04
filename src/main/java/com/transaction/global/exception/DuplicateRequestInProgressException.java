package com.transaction.global.exception;

import lombok.Getter;

@Getter
public class DuplicateRequestInProgressException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateRequestInProgressException(String idempotencyKey) {
        super("동일 요청이 처리 중입니다: key=" + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }
}
