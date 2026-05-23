package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasTransferDetailResponse {

  private Long transferId;
  private Long fromAccountId;
  private String toBankCode;
  private String toAccountNumber;
  private BigDecimal transferAmount;
  private String transferStatus;
  private String failureReason;
  private LocalDateTime requestedAt;
  private LocalDateTime completedAt;
}
