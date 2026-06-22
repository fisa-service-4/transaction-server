package com.transaction.domain.bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.AccountValidateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.mapping.entity.UserAccountMappingId;
import com.transaction.domain.mapping.repository.TransferUserMappingRepository;
import com.transaction.domain.mapping.repository.UserAccountMappingRepository;
import com.transaction.domain.saga.service.SagaOrchestrator;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.domain.stock.dto.response.BaasStockAccountItemResponse;
import com.transaction.domain.stock.dto.response.BaasStockAccountListResponse;
import com.transaction.global.config.BrokerCodeProperties;
import com.transaction.global.exception.SagaException;
import com.transaction.global.resolver.UserResolver;
import com.transaction.global.response.ApiResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaasTransferServiceTest {

  @Mock private BankCoreClient bankCoreClient;
  @Mock private StockCoreClient stockCoreClient;
  @Mock private UserResolver userResolver;
  @Mock private TransferUserMappingRepository transferUserMappingRepository;
  @Mock private UserAccountMappingRepository userAccountMappingRepository;
  @Mock private SagaOrchestrator sagaOrchestrator;
  @Mock private BrokerCodeProperties brokerCodeProperties;

  private BaasTransferService baasTransferService;

  private static final Long FROM_ACCOUNT_ID = 1001L;
  private static final Long STOCK_FROM_ACCOUNT_ID = 2001L;
  private static final Long X_USER_ID = 100L;
  private static final Long SETTLEMENT_ACCOUNT_ID = 9001L;
  private static final Long SETTLEMENT_X_USER_ID = 201L;
  private static final String TRACE_ID = "trace-service-001";
  private static final String IDEM_KEY = "idem-service-001";
  private static final String BANK_CODE = "088";
  private static final String BROKER_CODE = "243";
  private static final Long TRANSFER_ID = 5001L;

  @BeforeEach
  void setUp() {
    baasTransferService =
        new BaasTransferService(
            bankCoreClient,
            stockCoreClient,
            userResolver,
            transferUserMappingRepository,
            userAccountMappingRepository,
            sagaOrchestrator,
            brokerCodeProperties);
  }

  private BaasTransferRequest bankToBankRequest() {
    return BaasTransferRequest.builder()
        .fromAccountId(FROM_ACCOUNT_ID)
        .toBankCode(BANK_CODE)
        .toAccountNumber("110-123-456789")
        .transferAmount(BigDecimal.valueOf(100000))
        .requestedBy("USER")
        .build();
  }

  private BaasTransferRequest bankToStockRequest() {
    return BaasTransferRequest.builder()
        .fromAccountId(FROM_ACCOUNT_ID)
        .toBankCode(BROKER_CODE)
        .toAccountNumber("300-777-000071")
        .transferAmount(BigDecimal.valueOf(100000))
        .requestedBy("USER")
        .build();
  }

  private BaasTransferRequest stockToBankRequest() {
    return BaasTransferRequest.builder()
        .fromAccountId(STOCK_FROM_ACCOUNT_ID)
        .toBankCode(BANK_CODE)
        .toAccountNumber("110-123-456789")
        .transferAmount(BigDecimal.valueOf(100000))
        .requestedBy("USER")
        .build();
  }

  private ApiResponse<AccountValidateResponse> validAccountResponse() {
    AccountValidateResponse resp = mock(AccountValidateResponse.class);
    when(resp.isValidYn()).thenReturn(true);
    return ApiResponse.success(resp, TRACE_ID);
  }

  private ApiResponse<AccountValidateResponse> invalidAccountResponse() {
    AccountValidateResponse resp = mock(AccountValidateResponse.class);
    when(resp.isValidYn()).thenReturn(false);
    return ApiResponse.success(resp, TRACE_ID);
  }

  private ApiResponse<BaasStockAccountListResponse> stockAccountListResponse(
      Long accountId, String bankCode) {
    BaasStockAccountItemResponse item = mock(BaasStockAccountItemResponse.class);
    when(item.getAccountId()).thenReturn(accountId);
    when(item.getAccountNumber()).thenReturn("300-777-000071");
    when(item.getBankCode()).thenReturn(bankCode);
    BaasStockAccountListResponse list = mock(BaasStockAccountListResponse.class);
    when(list.getContent()).thenReturn(List.of(item));
    return ApiResponse.success(list, TRACE_ID);
  }

  private ApiResponse<BaasTransferCreateResponse> createTransferResponse() {
    return ApiResponse.success(
        new BaasTransferCreateResponse(TRANSFER_ID, "REQUESTED", LocalDateTime.now()), TRACE_ID);
  }

  @Test
  void createTransfer_BankToBank_직접이체() {
    ApiResponse<AccountValidateResponse> validResp = validAccountResponse();
    ApiResponse<BaasTransferCreateResponse> transferResp = createTransferResponse();
    when(userAccountMappingRepository.existsById(any(UserAccountMappingId.class)))
        .thenReturn(false);
    when(brokerCodeProperties.getCodes()).thenReturn(Set.of(BROKER_CODE));
    when(userResolver.resolveByAccount(FROM_ACCOUNT_ID, "BANK")).thenReturn(X_USER_ID);
    when(bankCoreClient.validateAccount(anyLong(), anyString(), any())).thenReturn(validResp);
    when(bankCoreClient.createTransfer(anyLong(), anyString(), anyString(), any()))
        .thenReturn(transferResp);

    ApiResponse<BaasTransferCreateResponse> result =
        baasTransferService.createTransfer(TRACE_ID, IDEM_KEY, bankToBankRequest());

    assertThat(result.getData().getTransferId()).isEqualTo(TRANSFER_ID);
    verify(bankCoreClient).createTransfer(anyLong(), anyString(), anyString(), any());
    verify(sagaOrchestrator, never()).bankToStock(any(), any(), any(), any(), any());
    verify(sagaOrchestrator, never()).stockToBank(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void createTransfer_BankToStock_Saga위임() {
    ApiResponse<AccountValidateResponse> validResp = validAccountResponse();
    BaasTransferCreateResponse sagaResult =
        new BaasTransferCreateResponse(TRANSFER_ID, "SUCCESS", LocalDateTime.now());
    when(userAccountMappingRepository.existsById(any(UserAccountMappingId.class)))
        .thenReturn(false);
    when(brokerCodeProperties.getCodes()).thenReturn(Set.of(BROKER_CODE));
    when(userResolver.resolveByAccount(FROM_ACCOUNT_ID, "BANK")).thenReturn(X_USER_ID);
    when(stockCoreClient.validateAccount(anyLong(), anyString(), any())).thenReturn(validResp);
    when(sagaOrchestrator.bankToStock(any(), anyString(), anyLong(), anyString(), anyString()))
        .thenReturn(sagaResult);

    ApiResponse<BaasTransferCreateResponse> result =
        baasTransferService.createTransfer(TRACE_ID, IDEM_KEY, bankToStockRequest());

    assertThat(result.getData().getTransferStatus()).isEqualTo("SUCCESS");
    verify(sagaOrchestrator).bankToStock(any(), anyString(), anyLong(), anyString(), anyString());
    verify(bankCoreClient, never()).createTransfer(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void createTransfer_StockToBank_Saga위임() {
    ApiResponse<AccountValidateResponse> validResp = validAccountResponse();
    ApiResponse<BaasStockAccountListResponse> stockListResp =
        stockAccountListResponse(STOCK_FROM_ACCOUNT_ID, BROKER_CODE);
    BaasTransferCreateResponse sagaResult =
        new BaasTransferCreateResponse(TRANSFER_ID, "SUCCESS", LocalDateTime.now());
    when(userAccountMappingRepository.existsById(any(UserAccountMappingId.class))).thenReturn(true);
    when(brokerCodeProperties.getCodes()).thenReturn(Set.of(BROKER_CODE));
    when(userResolver.resolveByAccount(STOCK_FROM_ACCOUNT_ID, "STOCK")).thenReturn(X_USER_ID);
    when(bankCoreClient.validateAccount(anyLong(), anyString(), any())).thenReturn(validResp);
    when(stockCoreClient.getStockAccounts(anyLong(), anyString())).thenReturn(stockListResp);
    when(brokerCodeProperties.getSettlementAccountId(BROKER_CODE))
        .thenReturn(SETTLEMENT_ACCOUNT_ID);
    when(brokerCodeProperties.getSettlementXUserId()).thenReturn(SETTLEMENT_X_USER_ID);
    when(sagaOrchestrator.stockToBank(
            any(), anyString(), anyLong(), anyString(), anyString(), anyLong(), anyLong()))
        .thenReturn(sagaResult);

    ApiResponse<BaasTransferCreateResponse> result =
        baasTransferService.createTransfer(TRACE_ID, IDEM_KEY, stockToBankRequest());

    assertThat(result.getData().getTransferStatus()).isEqualTo("SUCCESS");
    verify(sagaOrchestrator)
        .stockToBank(any(), anyString(), anyLong(), anyString(), anyString(), anyLong(), anyLong());
    verify(bankCoreClient, never()).createTransfer(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void createTransfer_StockToStock_예외() {
    when(userAccountMappingRepository.existsById(any(UserAccountMappingId.class))).thenReturn(true);
    when(brokerCodeProperties.getCodes()).thenReturn(Set.of(BROKER_CODE));

    BaasTransferRequest req =
        BaasTransferRequest.builder()
            .fromAccountId(STOCK_FROM_ACCOUNT_ID)
            .toBankCode(BROKER_CODE)
            .toAccountNumber("300-888-000001")
            .transferAmount(BigDecimal.valueOf(100000))
            .requestedBy("USER")
            .build();

    assertThatThrownBy(() -> baasTransferService.createTransfer(TRACE_ID, IDEM_KEY, req))
        .isInstanceOf(SagaException.class);

    verify(sagaOrchestrator, never()).bankToStock(any(), any(), any(), any(), any());
    verify(sagaOrchestrator, never()).stockToBank(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void createTransfer_계좌validate실패_예외() {
    ApiResponse<AccountValidateResponse> invalidResp = invalidAccountResponse();
    when(userAccountMappingRepository.existsById(any(UserAccountMappingId.class)))
        .thenReturn(false);
    when(brokerCodeProperties.getCodes()).thenReturn(Set.of(BROKER_CODE));
    when(userResolver.resolveByAccount(FROM_ACCOUNT_ID, "BANK")).thenReturn(X_USER_ID);
    when(bankCoreClient.validateAccount(anyLong(), anyString(), any())).thenReturn(invalidResp);

    assertThatThrownBy(
            () -> baasTransferService.createTransfer(TRACE_ID, IDEM_KEY, bankToBankRequest()))
        .isInstanceOf(SagaException.class);

    verify(bankCoreClient, never()).createTransfer(anyLong(), anyString(), anyString(), any());
  }

  @Test
  void createTransfer_정산계좌미설정_예외() {
    ApiResponse<AccountValidateResponse> validResp = validAccountResponse();
    ApiResponse<BaasStockAccountListResponse> stockListResp =
        stockAccountListResponse(STOCK_FROM_ACCOUNT_ID, BROKER_CODE);
    when(userAccountMappingRepository.existsById(any(UserAccountMappingId.class))).thenReturn(true);
    when(brokerCodeProperties.getCodes()).thenReturn(Set.of(BROKER_CODE));
    when(userResolver.resolveByAccount(STOCK_FROM_ACCOUNT_ID, "STOCK")).thenReturn(X_USER_ID);
    when(bankCoreClient.validateAccount(anyLong(), anyString(), any())).thenReturn(validResp);
    when(stockCoreClient.getStockAccounts(anyLong(), anyString())).thenReturn(stockListResp);
    when(brokerCodeProperties.getSettlementAccountId(BROKER_CODE)).thenReturn(null);

    assertThatThrownBy(
            () -> baasTransferService.createTransfer(TRACE_ID, IDEM_KEY, stockToBankRequest()))
        .isInstanceOf(SagaException.class);

    verify(sagaOrchestrator, never()).stockToBank(any(), any(), any(), any(), any(), any(), any());
  }
}
