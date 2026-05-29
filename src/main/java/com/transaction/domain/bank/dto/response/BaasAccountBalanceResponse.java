package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasAccountBalanceResponse {

  private Long accountId;
  private BigDecimal balance;
  private String updatedAt;
}
