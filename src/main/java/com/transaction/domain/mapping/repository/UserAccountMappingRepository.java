package com.transaction.domain.mapping.repository;

import com.transaction.domain.mapping.entity.UserAccountMapping;
import com.transaction.domain.mapping.entity.UserAccountMappingId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountMappingRepository
    extends JpaRepository<UserAccountMapping, UserAccountMappingId> {}
