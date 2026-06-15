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
    Long xUserId = userResolver.resolveByAccount(accountId, "BANK");
    log.info(
        "[BaasCardService] getCardsByAccount 시작: xUserId={}, traceId={}, accountId={}",
        xUserId,
        traceId,
        accountId);

    ApiResponse<BaasCardListResponse> response =
        bankCoreClient.getCardsByAccount(xUserId, traceId, accountId);

    BaasCardListResponse data = response != null ? response.getData() : null;
    int count = (data != null && data.getContent() != null) ? data.getContent().size() : 0;
    log.info(
        "[BaasCardService] card-server getCardsByAccount 완료: accountId={}, count={}",
        accountId,
        count);

    return ApiResponse.success(data, traceId);
  }

  public ApiResponse<PageResponse<BaasCardApprovalResponse>> getCardApprovals(
      String traceId,
      Long cardId,
      String fromDate,
      String toDate,
      String approvalStatus,
      String merchantCategory,
      Integer page,
      Integer size) {
    Long xUserId = 0L;
    log.info(
        "[BaasCardService] getCardApprovals 시작: xUserId={}, traceId={}, cardId={}",
        xUserId,
        traceId,
        cardId);

    ApiResponse<PageResponse<BaasCardApprovalResponse>> response =
        bankCoreClient.getCardApprovals(
            xUserId,
            traceId,
            cardId,
            fromDate,
            toDate,
            approvalStatus,
            merchantCategory,
            page,
            size);

    PageResponse<BaasCardApprovalResponse> data = response != null ? response.getData() : null;
    long totalElements = data != null ? data.getTotalElements() : 0L;
    log.info(
        "[BaasCardService] card-server getCardApprovals 완료: cardId={}, totalElements={}",
        cardId,
        totalElements);

    return ApiResponse.success(data, traceId);
  }
}
