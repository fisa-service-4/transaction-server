package com.transaction.domain.reconciliation.scheduler;

import com.transaction.domain.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

  private final ReconciliationService reconciliationService;

  @Scheduled(fixedDelay = 3600000)
  public void run() {
    log.info("[ReconciliationScheduler] Saga 이상 상태 검증 시작");
    reconciliationService.check();
  }
}
