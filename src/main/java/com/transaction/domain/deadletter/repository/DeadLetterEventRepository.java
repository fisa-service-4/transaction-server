package com.transaction.domain.deadletter.repository;

import com.transaction.domain.deadletter.entity.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {
}
