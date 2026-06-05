package com.transaction.domain.saga.repository;

import com.transaction.domain.saga.entity.SagaTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaTransactionRepository extends JpaRepository<SagaTransaction, Long> {

  Optional<SagaTransaction> findByTransactionKey(String transactionKey);
}
