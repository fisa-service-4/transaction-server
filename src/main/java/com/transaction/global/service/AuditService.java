package com.transaction.global.service;

import com.transaction.global.entity.TransactionAuditLog;
import com.transaction.global.repository.TransactionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final TransactionAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String transactionType,
            String transactionKey,
            String status,
            String sourceSystem,
            String targetSystem,
            String requestPayload,
            String responsePayload) {
        TransactionAuditLog auditLog = TransactionAuditLog.create(
                transactionType, transactionKey, status, sourceSystem, targetSystem,
                requestPayload, responsePayload);
        auditLogRepository.save(auditLog);
        log.info("[AuditService] 감사 로그 기록: type={}, key={}, status={}",
                transactionType, transactionKey, status);
    }
}
