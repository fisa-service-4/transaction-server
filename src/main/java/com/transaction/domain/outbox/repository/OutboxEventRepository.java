package com.transaction.domain.outbox.repository;

import com.transaction.domain.outbox.entity.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedYnFalseOrderByCreatedAt(Pageable pageable);
}
