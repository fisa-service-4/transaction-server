package com.transaction.domain.card.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasCardApprovalResponse {

  private Long approvalId;
  private Long accountTransactionId;
  private String merchantName;
  private String merchantCategory;
  private BigDecimal approvalAmount;
  private String approvalStatus;
  private String approvalCode;
  private LocalDateTime approvedAt;
}
