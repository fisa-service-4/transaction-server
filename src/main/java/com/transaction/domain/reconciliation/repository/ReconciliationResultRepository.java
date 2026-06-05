package com.transaction.domain.reconciliation.repository;

import com.transaction.domain.reconciliation.entity.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {}
