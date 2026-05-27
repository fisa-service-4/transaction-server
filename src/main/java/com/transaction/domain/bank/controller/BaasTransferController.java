package com.transaction.domain.bank.controller;

import com.transaction.domain.bank.dto.request.BaasTransferRequest;
import com.transaction.domain.bank.dto.response.BaasTransferApproveResponse;
import com.transaction.domain.bank.dto.response.BaasTransferCreateResponse;
import com.transaction.domain.bank.dto.response.BaasTransferDetailResponse;
import com.transaction.domain.bank.service.BaasTransferService;
import com.transaction.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/baas/v1/bank/transfers")
@Tag(name = "BaaS Transfer", description = "은행 이체 BaaS API")
public class BaasTransferController {

  private final BaasTransferService baasTransferService;

  @Operation(summary = "이체 실행")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<BaasTransferCreateResponse> createTransfer(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody BaasTransferRequest request,
      HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasTransferController] POST /baas/v1/bank/transfers 요청: traceId={}, idempotencyKey={}",
        traceId,
        idempotencyKey);

    ApiResponse<BaasTransferCreateResponse> response =
        baasTransferService.createTransfer(traceId, idempotencyKey, request);

    log.info(
        "[BaasTransferController] POST /baas/v1/bank/transfers 완료: transferId={}, traceId={}",
        response.getData().getTransferId(),
        traceId);

    return response;
  }

  @Operation(summary = "이체 승인")
  @PostMapping("/{transferId}/approve")
  public ApiResponse<BaasTransferApproveResponse> approveTransfer(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @PathVariable Long transferId,
      HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasTransferController] POST /baas/v1/bank/transfers/{}/approve 요청: traceId={}, idempotencyKey={}",
        transferId,
        traceId,
        idempotencyKey);

    ApiResponse<BaasTransferApproveResponse> response =
        baasTransferService.approveTransfer(traceId, idempotencyKey, transferId);

    log.info(
        "[BaasTransferController] POST /baas/v1/bank/transfers/{}/approve 완료: traceId={}",
        transferId,
        traceId);

    return response;
  }

  @Operation(summary = "이체 결과 조회")
  @GetMapping("/{transferId}")
  public ApiResponse<BaasTransferDetailResponse> getTransfer(
      @PathVariable Long transferId, HttpServletRequest httpRequest) {
    String traceId = generateTraceId();
    httpRequest.setAttribute("traceId", traceId);

    log.info(
        "[BaasTransferController] GET /baas/v1/bank/transfers/{} 요청: traceId={}",
        transferId,
        traceId);

    ApiResponse<BaasTransferDetailResponse> response =
        baasTransferService.getTransfer(traceId, transferId);

    log.info(
        "[BaasTransferController] GET /baas/v1/bank/transfers/{} 완료: traceId={}",
        transferId,
        traceId);

    return response;
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString();
  }
}
