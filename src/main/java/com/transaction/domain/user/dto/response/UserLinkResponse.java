package com.transaction.domain.user.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserLinkResponse {

  private final Long userId;
  private final boolean linked;

  public static UserLinkResponse of(Long userId) {
    return new UserLinkResponse(userId, true);
  }
}
