package com.transaction.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasTransactionCategoryResponse {

  private String category;
  private BigDecimal totalAmount;
  private int count;
}
