package com.transaction.domain.card.controller;

import com.transaction.domain.card.dto.response.BaasCardApprovalResponse;
import com.transaction.domain.card.dto.response.BaasCardListResponse;
import com.transaction.domain.card.service.BaasCardService;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/baas/v1/card")
@Tag(name = "BaaS Card", description = "카드 BaaS API")
public class BaasCardController {

  private final BaasCardService baasCardService;

  @Operation(summary = "계좌별 카드 목록 조회")
  @GetMapping("/accounts/{accountId}/cards")
  public ApiResponse<BaasCardListResponse> getCardsByAccount(
      @PathVariable Long accountId, HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasCardController] GET /baas/v1/card/accounts/{}/cards 요청: traceId={}",
        accountId,
        traceId);

    ApiResponse<BaasCardListResponse> response =
        baasCardService.getCardsByAccount(traceId, accountId);

    log.info(
        "[BaasCardController] GET /baas/v1/card/accounts/{}/cards 완료: traceId={}",
        accountId,
        traceId);

    return response;
  }

  @Operation(summary = "카드 승인 내역 조회")
  @GetMapping("/cards/{cardId}/approvals")
  public ApiResponse<PageResponse<BaasCardApprovalResponse>> getCardApprovals(
      @PathVariable Long cardId,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) String approvalStatus,
      @RequestParam(required = false) String merchantCategory,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasCardController] GET /baas/v1/card/cards/{}/approvals 요청: traceId={}",
        cardId,
        traceId);

    ApiResponse<PageResponse<BaasCardApprovalResponse>> response =
        baasCardService.getCardApprovals(
            traceId, cardId, fromDate, toDate, approvalStatus, merchantCategory, page, size);

    log.info(
        "[BaasCardController] GET /baas/v1/card/cards/{}/approvals 완료: traceId={}",
        cardId,
        traceId);

    return response;
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString();
  }
}
