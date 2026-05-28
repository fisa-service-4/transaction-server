package com.transaction.global.resolver;

import com.transaction.domain.mapping.entity.UserAccountMappingId;
import com.transaction.domain.mapping.repository.OrderUserMappingRepository;
import com.transaction.domain.mapping.repository.TransferUserMappingRepository;
import com.transaction.domain.mapping.repository.UserAccountMappingRepository;
import com.transaction.domain.user.repository.UserMasterRepository;
import com.transaction.global.exception.UserMappingNotFoundException;
import com.transaction.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserResolver {

  private static final String SYSTEM_ACCOUNT_TYPE = "SYSTEM";
  private static final long SYSTEM_ACCOUNT_ID = 0L;

  private final UserAccountMappingRepository userAccountMappingRepository;
  private final TransferUserMappingRepository transferUserMappingRepository;
  private final OrderUserMappingRepository orderUserMappingRepository;
  private final UserMasterRepository userMasterRepository;

  public Long resolveByAccount(Long accountId, String accountType) {
    return userAccountMappingRepository
        .findById(new UserAccountMappingId(accountId, accountType))
        .orElseThrow(
            () ->
                new UserMappingNotFoundException(
                    "계정 매핑을 찾을 수 없습니다. accountId=" + accountId + ", type=" + accountType))
        .getXUserId();
  }

  public Long resolveByTransferId(Long transferId) {
    return transferUserMappingRepository
        .findById(transferId)
        .orElseThrow(
            () ->
                new UserMappingNotFoundException(
                    "이체 매핑을 찾을 수 없습니다. transferId=" + transferId))
        .getXUserId();
  }

  public Long resolveByOrderId(Long orderId) {
    return orderUserMappingRepository
        .findById(orderId)
        .orElseThrow(
            () ->
                new UserMappingNotFoundException("주문 매핑을 찾을 수 없습니다. orderId=" + orderId))
        .getXUserId();
  }

  public Long systemUserId() {
    return resolveByAccount(SYSTEM_ACCOUNT_ID, SYSTEM_ACCOUNT_TYPE);
  }

  public Long resolveByFirebaseUid(String firebaseUid) {
    return userMasterRepository
        .findByFirebaseUid(firebaseUid)
        .orElseThrow(
            () ->
                new UserNotFoundException(
                    "사용자를 찾을 수 없습니다. firebaseUid=" + firebaseUid))
        .getXUserId();
  }
}
