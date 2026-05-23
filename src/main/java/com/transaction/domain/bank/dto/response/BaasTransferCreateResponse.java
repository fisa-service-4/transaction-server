package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasTransferCreateResponse {

  private Long transferId;
  private String transferStatus;
  private LocalDateTime requestedAt;
}
