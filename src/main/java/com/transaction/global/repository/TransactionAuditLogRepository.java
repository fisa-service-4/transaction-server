package com.transaction.global.repository;

import com.transaction.global.entity.TransactionAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionAuditLogRepository extends JpaRepository<TransactionAuditLog, Long> {
}
