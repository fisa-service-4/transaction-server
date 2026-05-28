package com.transaction.domain.user.service;

import com.transaction.domain.user.dto.request.UserLinkRequest;
import com.transaction.domain.user.dto.response.UserLinkResponse;
import com.transaction.domain.user.entity.UserMaster;
import com.transaction.domain.user.repository.UserMasterRepository;
import com.transaction.global.exception.UserNotFoundException;
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

    UserMaster user =
        userMasterRepository
            .findByUserNameAndPhoneNumber(request.getName(), request.getPhoneNumber())
            .orElseThrow(
                () ->
                    new UserNotFoundException(
                        "사용자를 찾을 수 없습니다. name="
                            + request.getName()
                            + ", phoneNumber="
                            + request.getPhoneNumber()));

    user.link(request.getFirebaseUid());

    log.info("[UserLinkService] link 완료: userId={}", user.getUserId());

    return UserLinkResponse.of(user.getUserId());
  }
}
