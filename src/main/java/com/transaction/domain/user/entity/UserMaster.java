package com.transaction.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_master")
@Getter
@NoArgsConstructor
public class UserMaster {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "x_user_id", nullable = false)
  private Long xUserId;

  @Column(name = "firebase_uid", unique = true)
  private String firebaseUid;

  @Column(name = "user_name", nullable = false)
  private String userName;

  @Column(name = "phone_number", nullable = false)
  private String phoneNumber;

  @Column(name = "linked_at")
  private LocalDateTime linkedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static UserMaster create(
      Long userId, Long xUserId, String firebaseUid, String userName, String phoneNumber) {
    UserMaster user = new UserMaster();
    user.userId = userId;
    user.xUserId = xUserId;
    user.firebaseUid = firebaseUid;
    user.userName = userName;
    user.phoneNumber = phoneNumber;
    user.linkedAt = LocalDateTime.now();
    return user;
  }

  public void link(String firebaseUid) {
    this.firebaseUid = firebaseUid;
    this.linkedAt = LocalDateTime.now();
  }
}
