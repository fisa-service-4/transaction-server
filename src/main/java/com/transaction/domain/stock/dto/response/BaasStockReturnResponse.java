package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockReturnResponse {

  private Long accountId;
  private BigDecimal dailyReturnRate;
  private BigDecimal monthlyReturnRate;
  private BigDecimal yearlyReturnRate;
}
