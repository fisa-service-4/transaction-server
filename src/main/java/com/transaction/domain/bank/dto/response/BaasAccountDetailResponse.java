package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasAccountDetailResponse {

  private Long accountId;
  private Long userId;
  private String bankCode;
  private String accountNumber;
  private String accountName;
  private BigDecimal balance;
  private BigDecimal availableBalance;
  private String accountStatus;
  private LocalDateTime openedAt;
  private LocalDateTime closedAt;
  private LocalDateTime updatedAt;
}
