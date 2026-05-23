package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasTransactionResponse {

  private Long transactionId;
  private String transactionType;
  private String transactionCategory;
  private BigDecimal amount;
  private BigDecimal balanceAfter;
  private String transactionChannel;
  private String transactionStatus;
  private LocalDateTime transactionAt;
}
