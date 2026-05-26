package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockSearchItemResponse {

  private String stockCode;
  private String stockName;
  private String market;
  private BigDecimal currentPrice;
  private BigDecimal changeRate;
}
