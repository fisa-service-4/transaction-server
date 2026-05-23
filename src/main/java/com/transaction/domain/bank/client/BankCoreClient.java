package com.transaction.domain.bank.client;

import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasAccountDetailResponse;
import com.transaction.domain.bank.dto.response.BaasAccountListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionCategoryListResponse;
import com.transaction.domain.bank.dto.response.BaasTransactionResponse;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.global.config.FeignConfig;
import com.transaction.global.response.ApiResponse;
import com.transaction.global.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "bank-core-client", url = "${core.bank.url}", configuration = FeignConfig.class)
public interface BankCoreClient {

  @GetMapping("/internal/v1/bank/accounts")
  ApiResponse<BaasAccountListResponse> getAccounts(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestParam(required = false) String status);

  @GetMapping("/internal/v1/bank/accounts/{accountId}")
  ApiResponse<BaasAccountDetailResponse> getAccount(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId);

  @GetMapping("/internal/v1/bank/accounts/{accountId}/transactions")
  ApiResponse<PageResponse<BaasTransactionResponse>> getTransactions(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping("/internal/v1/bank/accounts/{accountId}/transactions/filter")
  ApiResponse<PageResponse<BaasTransactionResponse>> getTransactionsFilter(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) String minAmount,
      @RequestParam(required = false) String maxAmount,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size);

  @GetMapping("/internal/v1/bank/accounts/{accountId}/transactions/categories")
  ApiResponse<BaasTransactionCategoryListResponse> getTransactionCategories(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("accountId") Long accountId,
      @RequestParam String fromDate,
      @RequestParam String toDate);

  @PostMapping("/internal/v1/bank/transfers")
  ApiResponse<BaasTransferCreateResponse> createTransfer(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody BaasTransferRequest request);

  @PostMapping("/internal/v1/bank/transfers/{transferId}/approve")
  ApiResponse<BaasTransferApproveResponse> approveTransfer(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @PathVariable("transferId") Long transferId);

  @GetMapping("/internal/v1/bank/transfers/{transferId}")
  ApiResponse<BaasTransferDetailResponse> getTransfer(
      @RequestHeader("X-User-Id") Long userId,
      @RequestHeader("X-Trace-Id") String traceId,
      @PathVariable("transferId") Long transferId);
}
