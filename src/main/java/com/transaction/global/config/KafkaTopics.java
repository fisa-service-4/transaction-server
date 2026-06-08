package com.transaction.global.config;

public class KafkaTopics {

  public static final String SAGA_STARTED = "saga.started";
  public static final String SAGA_COMPLETED = "saga.completed";
  public static final String SAGA_FAILED = "saga.failed";
  public static final String SAGA_COMPENSATED = "saga.compensated";
  public static final String SAGA_COMPENSATION_FAILED = "saga.compensation_failed";

  private KafkaTopics() {}
}
