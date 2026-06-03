package com.transaction.domain.saga.repository;

import com.transaction.domain.saga.entity.SagaStepHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStepHistoryRepository extends JpaRepository<SagaStepHistory, Long> {

    List<SagaStepHistory> findBySagaIdOrderByStepOrder(Long sagaId);
}
