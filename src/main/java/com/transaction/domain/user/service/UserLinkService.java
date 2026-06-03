package com.transaction.domain.user.service;

import com.transaction.domain.user.dto.request.UserLinkRequest;
import com.transaction.domain.user.dto.response.UserLinkResponse;
import com.transaction.domain.user.entity.UserMaster;
import com.transaction.domain.user.repository.UserMasterRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLinkService {

  private final UserMasterRepository userMasterRepository;

  @Transactional
  public UserLinkResponse link(UserLinkRequest request) {
    log.info("[UserLinkService] link 시작: name={}", request.getName());

    Optional<UserMaster> existing =
        userMasterRepository.findByUserNameAndPhoneNumber(
            request.getName(), request.getPhoneNumber());

    UserMaster user;
    if (existing.isPresent()) {
      user = existing.get();
      user.link(request.getFirebaseUid());
      log.info("[UserLinkService] 기존 유저 연동 완료: userId={}", user.getUserId());
    } else {
      user =
          UserMaster.create(
              request.getUserId(),
              request.getUserId(),
              request.getFirebaseUid(),
              request.getName(),
              request.getPhoneNumber());
      userMasterRepository.save(user);
      log.info("[UserLinkService] 신규 유저 생성 완료: userId={}", user.getUserId());
    }

    return UserLinkResponse.of(user.getUserId());
  }
}
