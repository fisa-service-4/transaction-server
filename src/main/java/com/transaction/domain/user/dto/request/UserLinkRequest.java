package com.transaction.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserLinkRequest {

  @NotBlank
  private String firebaseUid;

  @NotBlank
  private String name;

  @NotBlank
  private String phoneNumber;
}
