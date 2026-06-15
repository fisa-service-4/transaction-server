package com.transaction.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.card.dto.response.BaasCardApprovalResponse;
import com.transaction.domain.card.dto.response.BaasCardItemResponse;
import com.transaction.domain.card.dto.response.BaasCardListResponse;
import com.transaction.global.exception.BankCoreException;
import com.transaction.global.exception.UserMappingNotFoundException;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BaasCardServiceTest {

  @InjectMocks private BaasCardService baasCardService;

  @Mock private BankCoreClient bankCoreClient;

  @Mock private UserResolver userResolver;

  private static final String TRACE_ID = "test-trace-id";
  private static final Long ACCOUNT_ID = 1001L;
  private static final Long CARD_ID = 1L;
  private static final Long USER_ID = 501L;

  @Nested
  @DisplayName("getCardsByAccount - 계좌별 카드 목록 조회")
  class GetCardsByAccount {

    @Test
    @DisplayName("정상 조회 - 카드 1건 반환")
    void success() {
      // given
      BaasCardItemResponse cardItem = new BaasCardItemResponse();
      ReflectionTestUtils.setField(cardItem, "cardId", 1L);
      ReflectionTestUtils.setField(cardItem, "cardNumber", "1234-****-****-5678");
      ReflectionTestUtils.setField(cardItem, "linkedAccountId", ACCOUNT_ID);
      ReflectionTestUtils.setField(cardItem, "cardStatus", "ACTIVE");

      BaasCardListResponse cardList = new BaasCardListResponse();
      ReflectionTestUtils.setField(cardList, "content", List.of(cardItem));

      given(userResolver.resolveByAccount(ACCOUNT_ID, "BANK")).willReturn(USER_ID);
      given(bankCoreClient.getCardsByAccount(USER_ID, TRACE_ID, ACCOUNT_ID))
          .willReturn(ApiResponse.success(cardList, TRACE_ID));

      // when
      ApiResponse<BaasCardListResponse> response =
          baasCardService.getCardsByAccount(TRACE_ID, ACCOUNT_ID);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData().getContent()).hasSize(1);
      assertThat(response.getData().getContent().get(0).getCardId()).isEqualTo(1L);
      assertThat(response.getData().getContent().get(0).getCardNumber())
          .isEqualTo("1234-****-****-5678");
      assertThat(response.getData().getContent().get(0).getCardStatus()).isEqualTo("ACTIVE");
      verify(userResolver).resolveByAccount(ACCOUNT_ID, "BANK");
      verify(bankCoreClient).getCardsByAccount(USER_ID, TRACE_ID, ACCOUNT_ID);
    }

    @Test
    @DisplayName("연결 카드 없는 계좌 - 빈 목록 반환")
    void emptyCards() {
      // given
      BaasCardListResponse emptyList = new BaasCardListResponse();

      given(userResolver.resolveByAccount(ACCOUNT_ID, "BANK")).willReturn(USER_ID);
      given(bankCoreClient.getCardsByAccount(USER_ID, TRACE_ID, ACCOUNT_ID))
          .willReturn(ApiResponse.success(emptyList, TRACE_ID));

      // when
      ApiResponse<BaasCardListResponse> response =
          baasCardService.getCardsByAccount(TRACE_ID, ACCOUNT_ID);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData().getContent()).isEmpty();
    }

    @Test
    @DisplayName("계좌-사용자 매핑 없을 때 UserMappingNotFoundException 발생")
    void userMappingNotFound() {
      // given
      given(userResolver.resolveByAccount(ACCOUNT_ID, "BANK"))
          .willThrow(
              new UserMappingNotFoundException(
                  "계정 매핑을 찾을 수 없습니다. accountId=" + ACCOUNT_ID + ", type=BANK"));

      // when & then
      assertThatThrownBy(() -> baasCardService.getCardsByAccount(TRACE_ID, ACCOUNT_ID))
          .isInstanceOf(UserMappingNotFoundException.class)
          .hasMessageContaining("계정 매핑을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("bank-server 오류 시 BankCoreException 전파")
    void bankCoreException() {
      // given
      given(userResolver.resolveByAccount(ACCOUNT_ID, "BANK")).willReturn(USER_ID);
      given(bankCoreClient.getCardsByAccount(USER_ID, TRACE_ID, ACCOUNT_ID))
          .willThrow(
              new BankCoreException("ACCOUNT_001", "계좌를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

      // when & then
      assertThatThrownBy(() -> baasCardService.getCardsByAccount(TRACE_ID, ACCOUNT_ID))
          .isInstanceOf(BankCoreException.class)
          .hasMessageContaining("계좌를 찾을 수 없습니다");
    }
  }

  @Nested
  @DisplayName("getCardApprovals - 카드 승인 내역 조회")
  class GetCardApprovals {

    @Test
    @DisplayName("필터 없이 승인 내역 조회 성공")
    void success() {
      // given
      BaasCardApprovalResponse approval = new BaasCardApprovalResponse();
      ReflectionTestUtils.setField(approval, "approvalId", 50L);
      ReflectionTestUtils.setField(approval, "merchantName", "스타벅스 강남점");
      ReflectionTestUtils.setField(approval, "merchantCategory", "CAFE");
      ReflectionTestUtils.setField(approval, "approvalAmount", BigDecimal.valueOf(5500));
      ReflectionTestUtils.setField(approval, "approvalStatus", "APPROVED");
      ReflectionTestUtils.setField(approval, "approvedAt", LocalDateTime.of(2026, 5, 17, 12, 0, 0));

      PageResponse<BaasCardApprovalResponse> pageResponse = new PageResponse<>();
      ReflectionTestUtils.setField(pageResponse, "content", List.of(approval));
      ReflectionTestUtils.setField(pageResponse, "page", 0);
      ReflectionTestUtils.setField(pageResponse, "size", 20);
      ReflectionTestUtils.setField(pageResponse, "totalElements", 1L);
      ReflectionTestUtils.setField(pageResponse, "totalPages", 1);

      // xUserId는 서비스 내부에서 0L로 고정
      given(
              bankCoreClient.getCardApprovals(
                  eq(0L), eq(TRACE_ID), eq(CARD_ID),
                  isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
          .willReturn(ApiResponse.success(pageResponse, TRACE_ID));

      // when
      ApiResponse<PageResponse<BaasCardApprovalResponse>> response =
          baasCardService.getCardApprovals(
              TRACE_ID, CARD_ID, null, null, null, null, null, null);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData().getTotalElements()).isEqualTo(1L);
      assertThat(response.getData().getContent()).hasSize(1);
      assertThat(response.getData().getContent().get(0).getMerchantName()).isEqualTo("스타벅스 강남점");
      assertThat(response.getData().getContent().get(0).getApprovalStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("날짜·상태·카테고리 필터 적용 조회")
    void withFilters() {
      // given
      PageResponse<BaasCardApprovalResponse> pageResponse = new PageResponse<>();
      ReflectionTestUtils.setField(pageResponse, "content", List.of());
      ReflectionTestUtils.setField(pageResponse, "page", 0);
      ReflectionTestUtils.setField(pageResponse, "size", 20);
      ReflectionTestUtils.setField(pageResponse, "totalElements", 0L);
      ReflectionTestUtils.setField(pageResponse, "totalPages", 0);

      given(
              bankCoreClient.getCardApprovals(
                  eq(0L), eq(TRACE_ID), eq(CARD_ID),
                  eq("2026-05-01"), eq("2026-05-31"),
                  eq("APPROVED"), eq("CAFE"), eq(0), eq(20)))
          .willReturn(ApiResponse.success(pageResponse, TRACE_ID));

      // when
      ApiResponse<PageResponse<BaasCardApprovalResponse>> response =
          baasCardService.getCardApprovals(
              TRACE_ID, CARD_ID, "2026-05-01", "2026-05-31", "APPROVED", "CAFE", 0, 20);

      // then
      assertThat(response.isSuccess()).isTrue();
      assertThat(response.getData().getContent()).isEmpty();
      assertThat(response.getData().getTotalElements()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 카드 ID - BankCoreException(CARD_001) 전파")
    void cardNotFound() {
      // given
      given(
              bankCoreClient.getCardApprovals(
                  anyLong(), anyString(), anyLong(),
                  any(), any(), any(), any(), any(), any()))
          .willThrow(
              new BankCoreException("CARD_001", "카드를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

      // when & then
      assertThatThrownBy(
              () ->
                  baasCardService.getCardApprovals(
                      TRACE_ID, CARD_ID, null, null, null, null, null, null))
          .isInstanceOf(BankCoreException.class)
          .hasMessageContaining("카드를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("본인 카드가 아닐 때 - BankCoreException(CARD_002) 전파")
    void notOwnCard() {
      // given
      given(
              bankCoreClient.getCardApprovals(
                  anyLong(), anyString(), anyLong(),
                  any(), any(), any(), any(), any(), any()))
          .willThrow(
              new BankCoreException("CARD_002", "본인 카드가 아닙니다", HttpStatus.FORBIDDEN));

      // when & then
      assertThatThrownBy(
              () ->
                  baasCardService.getCardApprovals(
                      TRACE_ID, CARD_ID, null, null, null, null, null, null))
          .isInstanceOf(BankCoreException.class)
          .hasMessageContaining("본인 카드가 아닙니다");
    }
  }
}
