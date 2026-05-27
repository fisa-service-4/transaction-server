package com.transaction.domain.mapping.repository;

import com.transaction.domain.mapping.entity.TransferUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferUserMappingRepository extends JpaRepository<TransferUserMapping, Long> {}
