package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasTransferApproveResponse {

  private Long transferId;
  private String transferStatus;
  private LocalDateTime completedAt;
}
