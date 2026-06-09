package com.transaction.domain.card.service;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.card.dto.response.BaasCardApprovalResponse;
import com.transaction.domain.card.dto.response.BaasCardListResponse;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasCardService {

  private final BankCoreClient bankCoreClient;
  private final UserResolver userResolver;

  public ApiResponse<BaasCardListResponse> getCardsByAccount(String traceId, Long accountId) {
    log.info("[BaasCardService] getCardsByAccount 시작: traceId={}, accountId={}", traceId, accountId);

    ApiResponse<BaasCardListResponse> response =
        bankCoreClient.getCardsByAccount(traceId, accountId);

    log.info(
        "[BaasCardService] card-server getCardsByAccount 완료: accountId={}, count={}",
        accountId,
        response.getData().getContent() != null ? response.getData().getContent().size() : 0);

    return ApiResponse.success(response.getData(), traceId);
  }

  public ApiResponse<PageResponse<BaasCardApprovalResponse>> getCardApprovals(
      String traceId,
      Long cardId,
      String firebaseUid,
      String fromDate,
      String toDate,
      Integer page,
      Integer size) {
    Long xUserId = userResolver.resolveByFirebaseUid(firebaseUid);
    log.info(
        "[BaasCardService] getCardApprovals 시작: xUserId={}, traceId={}, cardId={}",
        xUserId,
        traceId,
        cardId);

    ApiResponse<PageResponse<BaasCardApprovalResponse>> response =
        bankCoreClient.getCardApprovals(xUserId, traceId, cardId, fromDate, toDate, page, size);

    log.info(
        "[BaasCardService] card-server getCardApprovals 완료: cardId={}, totalElements={}",
        cardId,
        response.getData().getTotalElements());

    return ApiResponse.success(response.getData(), traceId);
  }
}
