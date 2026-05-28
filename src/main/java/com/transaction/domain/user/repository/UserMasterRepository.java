package com.transaction.domain.user.repository;

import com.transaction.domain.user.entity.UserMaster;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMasterRepository extends JpaRepository<UserMaster, Long> {

  Optional<UserMaster> findByFirebaseUid(String firebaseUid);

  Optional<UserMaster> findByUserNameAndPhoneNumber(String userName, String phoneNumber);
}
