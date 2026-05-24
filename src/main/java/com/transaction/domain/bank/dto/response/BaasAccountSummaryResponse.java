package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasAccountSummaryResponse {

  private Long accountId;
  private Long userId;
  private String bankCode;
  private String accountNumber;
  private String accountName;
  private BigDecimal balance;
  private String accountStatus;
}
