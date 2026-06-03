package com.transaction.domain.reconciliation.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.ReconciliationRunRequest;
import com.transaction.domain.bank.dto.response.ReconciliationRunResponse;
import com.transaction.domain.reconciliation.entity.ReconciliationResult;
import com.transaction.domain.reconciliation.repository.ReconciliationResultRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Long SYSTEM_USER_ID = 0L;
    private static final String SOURCE = "transaction-server";
    private static final String TARGET = "bank-server";

    private final BankCoreClient bankCoreClient;
    private final ReconciliationResultRepository reconciliationResultRepository;

    @Transactional
    public ReconciliationResult run(String targetDate) {
        String traceId = UUID.randomUUID().toString();
        String date = (targetDate != null) ? targetDate
                : LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        log.info("[ReconciliationService] 정합성 검증 요청: targetDate={}, traceId={}", date, traceId);

        try {
            ReconciliationRunResponse bankResponse = bankCoreClient
                    .runReconciliation(SYSTEM_USER_ID, traceId, new ReconciliationRunRequest(date))
                    .getData();

            ReconciliationResult result = ReconciliationResult.create(
                    "BANK_TRANSACTION", SOURCE, TARGET,
                    null, null, null,
                    bankResponse.getStatus());
            ReconciliationResult saved = reconciliationResultRepository.save(result);

            log.info("[ReconciliationService] 정합성 검증 시작됨: reconciliationId={}, bankReconciliationId={}",
                    saved.getReconciliationId(), bankResponse.getReconciliationId());
            return saved;

        } catch (Exception e) {
            log.error("[ReconciliationService] 정합성 검증 요청 실패: error={}", e.getMessage());
            ReconciliationResult failed = ReconciliationResult.create(
                    "BANK_TRANSACTION", SOURCE, TARGET,
                    null, null, null, "FAILED");
            return reconciliationResultRepository.save(failed);
        }
    }

    @Transactional
    public ReconciliationResult runForToday() {
        return run(null);
    }
}
