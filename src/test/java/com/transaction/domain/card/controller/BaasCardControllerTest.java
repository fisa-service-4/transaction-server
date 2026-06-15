package com.transaction.domain.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transaction.domain.card.dto.response.BaasCardApprovalResponse;
import com.transaction.domain.card.dto.response.BaasCardItemResponse;
import com.transaction.domain.card.dto.response.BaasCardListResponse;
import com.transaction.domain.card.service.BaasCardService;
import com.transaction.global.exception.BankCoreException;
import com.transaction.global.exception.UserMappingNotFoundException;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BaasCardController.class)
class BaasCardControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private BaasCardService baasCardService;

  @Nested
  @DisplayName("GET /baas/v1/card/accounts/{accountId}/cards")
  class GetCardsByAccount {

    @Test
    @DisplayName("카드 1건 - 200 OK, 카드 정보 포함")
    void success() throws Exception {
      // given
      BaasCardItemResponse cardItem = new BaasCardItemResponse();
      ReflectionTestUtils.setField(cardItem, "cardId", 1L);
      ReflectionTestUtils.setField(cardItem, "cardNumber", "1234-****-****-5678");
      ReflectionTestUtils.setField(cardItem, "linkedAccountId", 1001L);
      ReflectionTestUtils.setField(cardItem, "cardStatus", "ACTIVE");

      BaasCardListResponse cardList = new BaasCardListResponse();
      ReflectionTestUtils.setField(cardList, "content", List.of(cardItem));

      given(baasCardService.getCardsByAccount(anyString(), eq(1001L)))
          .willReturn(ApiResponse.success(cardList, "trace-id"));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/accounts/1001/cards"))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.content").isArray())
          .andExpect(jsonPath("$.data.content[0].cardId").value(1))
          .andExpect(jsonPath("$.data.content[0].cardNumber").value("1234-****-****-5678"))
          .andExpect(jsonPath("$.data.content[0].cardStatus").value("ACTIVE"))
          .andExpect(jsonPath("$.meta.traceId").exists());
    }

    @Test
    @DisplayName("연결 카드 없음 - 200 OK, 빈 목록")
    void emptyCards() throws Exception {
      // given
      BaasCardListResponse emptyList = new BaasCardListResponse();
      given(baasCardService.getCardsByAccount(anyString(), eq(1001L)))
          .willReturn(ApiResponse.success(emptyList, "trace-id"));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/accounts/1001/cards"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.content").isArray())
          .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("계좌-사용자 매핑 없음 - 500, MAPPING_001")
    void userMappingNotFound() throws Exception {
      // given
      given(baasCardService.getCardsByAccount(anyString(), eq(9999L)))
          .willThrow(new UserMappingNotFoundException("계정 매핑을 찾을 수 없습니다. accountId=9999"));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/accounts/9999/cards"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("MAPPING_001"))
          .andExpect(jsonPath("$.error.message").value("계정 매핑을 찾을 수 없습니다. accountId=9999"));
    }

    @Test
    @DisplayName("bank-server 계좌 없음 - 404, ACCOUNT_001")
    void bankCoreAccountNotFound() throws Exception {
      // given
      given(baasCardService.getCardsByAccount(anyString(), eq(1001L)))
          .willThrow(
              new BankCoreException("ACCOUNT_001", "계좌를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/accounts/1001/cards"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("ACCOUNT_001"))
          .andExpect(jsonPath("$.error.message").value("계좌를 찾을 수 없습니다"));
    }
  }

  @Nested
  @DisplayName("GET /baas/v1/card/cards/{cardId}/approvals")
  class GetCardApprovals {

    @Test
    @DisplayName("승인 내역 1건 조회 - 200 OK")
    void success() throws Exception {
      // given
      BaasCardApprovalResponse approval = new BaasCardApprovalResponse();
      ReflectionTestUtils.setField(approval, "approvalId", 50L);
      ReflectionTestUtils.setField(approval, "merchantName", "스타벅스 강남점");
      ReflectionTestUtils.setField(approval, "merchantCategory", "CAFE");
      ReflectionTestUtils.setField(approval, "approvalAmount", BigDecimal.valueOf(5500));
      ReflectionTestUtils.setField(approval, "approvalStatus", "APPROVED");
      ReflectionTestUtils.setField(
          approval, "approvedAt", LocalDateTime.of(2026, 5, 17, 12, 0, 0));

      PageResponse<BaasCardApprovalResponse> pageResponse = new PageResponse<>();
      ReflectionTestUtils.setField(pageResponse, "content", List.of(approval));
      ReflectionTestUtils.setField(pageResponse, "page", 0);
      ReflectionTestUtils.setField(pageResponse, "size", 20);
      ReflectionTestUtils.setField(pageResponse, "totalElements", 1L);
      ReflectionTestUtils.setField(pageResponse, "totalPages", 1);

      given(
              baasCardService.getCardApprovals(
                  anyString(), eq(1L), any(), any(), any(), any(), any(), any()))
          .willReturn(ApiResponse.success(pageResponse, "trace-id"));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/cards/1/approvals"))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.content[0].approvalId").value(50))
          .andExpect(jsonPath("$.data.content[0].merchantName").value("스타벅스 강남점"))
          .andExpect(jsonPath("$.data.content[0].merchantCategory").value("CAFE"))
          .andExpect(jsonPath("$.data.content[0].approvalAmount").value(5500))
          .andExpect(jsonPath("$.data.content[0].approvalStatus").value("APPROVED"))
          .andExpect(jsonPath("$.data.totalElements").value(1))
          .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("날짜·상태·카테고리 쿼리 파라미터 - 200 OK, 빈 목록")
    void withQueryParams() throws Exception {
      // given
      PageResponse<BaasCardApprovalResponse> pageResponse = new PageResponse<>();
      ReflectionTestUtils.setField(pageResponse, "content", List.of());
      ReflectionTestUtils.setField(pageResponse, "page", 0);
      ReflectionTestUtils.setField(pageResponse, "size", 10);
      ReflectionTestUtils.setField(pageResponse, "totalElements", 0L);
      ReflectionTestUtils.setField(pageResponse, "totalPages", 0);

      given(
              baasCardService.getCardApprovals(
                  anyString(),
                  eq(1L),
                  eq("2026-05-01"),
                  eq("2026-05-31"),
                  eq("APPROVED"),
                  eq("CAFE"),
                  eq(0),
                  eq(10)))
          .willReturn(ApiResponse.success(pageResponse, "trace-id"));

      // when & then
      mockMvc
          .perform(
              get("/baas/v1/card/cards/1/approvals")
                  .param("fromDate", "2026-05-01")
                  .param("toDate", "2026-05-31")
                  .param("approvalStatus", "APPROVED")
                  .param("merchantCategory", "CAFE")
                  .param("page", "0")
                  .param("size", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.content").isEmpty())
          .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 카드 - 404, CARD_001")
    void cardNotFound() throws Exception {
      // given
      given(
              baasCardService.getCardApprovals(
                  anyString(), eq(9999L), any(), any(), any(), any(), any(), any()))
          .willThrow(
              new BankCoreException("CARD_001", "카드를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/cards/9999/approvals"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("CARD_001"))
          .andExpect(jsonPath("$.error.message").value("카드를 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("본인 카드 아님 - 403, CARD_002")
    void notOwnCard() throws Exception {
      // given
      given(
              baasCardService.getCardApprovals(
                  anyString(), eq(1L), any(), any(), any(), any(), any(), any()))
          .willThrow(new BankCoreException("CARD_002", "본인 카드가 아닙니다", HttpStatus.FORBIDDEN));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/cards/1/approvals"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("CARD_002"))
          .andExpect(jsonPath("$.error.message").value("본인 카드가 아닙니다"));
    }

    @Test
    @DisplayName("카드 승인 내역 없음 - 404, CARD_003")
    void approvalNotFound() throws Exception {
      // given
      given(
              baasCardService.getCardApprovals(
                  anyString(), eq(1L), any(), any(), any(), any(), any(), any()))
          .willThrow(
              new BankCoreException(
                  "CARD_003", "카드 승인 내역을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

      // when & then
      mockMvc
          .perform(get("/baas/v1/card/cards/1/approvals"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("CARD_003"));
    }
  }
}
