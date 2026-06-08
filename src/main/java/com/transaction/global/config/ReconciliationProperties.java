package com.transaction.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "saga.reconciliation")
public class ReconciliationProperties {

  private int processingTimeoutMinutes = 30;
  private int compensatingTimeoutMinutes = 10;
}
