package com.transaction.domain.bank.client;

import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.global.config.FeignConfig;
import com.transaction.global.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "bank-core-client", url = "${core.bank.url}", configuration = FeignConfig.class)
public interface BankCoreClient {

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
