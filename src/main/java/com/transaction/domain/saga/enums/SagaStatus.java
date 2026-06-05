package com.transaction.domain.saga.enums;

public enum SagaStatus {
  STARTED,
  PROCESSING,
  SUCCESS,
  FAILED,
  COMPENSATING,
  COMPENSATED,
  UNKNOWN,
  COMPENSATION_FAILED
}
