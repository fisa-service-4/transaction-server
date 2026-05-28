package com.transaction.domain.user.controller;

import com.transaction.domain.user.dto.request.UserLinkRequest;
import com.transaction.domain.user.dto.response.UserLinkResponse;
import com.transaction.domain.user.service.UserLinkService;
import com.transaction.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/baas/v1/user")
@Tag(name = "User", description = "사용자 연동 API")
public class UserLinkController {

  private final UserLinkService userLinkService;

  @Operation(summary = "Firebase UID 연동")
  @PostMapping("/link")
  @ResponseStatus(HttpStatus.OK)
  public ApiResponse<UserLinkResponse> link(
      @Valid @RequestBody UserLinkRequest request, HttpServletRequest httpRequest) {
    String traceId = UUID.randomUUID().toString();
    httpRequest.setAttribute("traceId", traceId);

    log.info("[UserLinkController] POST /baas/v1/user/link 요청: traceId={}", traceId);

    UserLinkResponse response = userLinkService.link(request);

    log.info("[UserLinkController] POST /baas/v1/user/link 완료: traceId={}", traceId);

    return ApiResponse.success(response, traceId);
  }
}
