package com.transaction.domain.mapping.repository;

import com.transaction.domain.mapping.entity.OrderUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderUserMappingRepository extends JpaRepository<OrderUserMapping, Long> {}
