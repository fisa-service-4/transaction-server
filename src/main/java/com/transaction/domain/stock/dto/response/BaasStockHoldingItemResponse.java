package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockHoldingItemResponse {

  private String stockCode;
  private String stockName;
  private Integer quantity;
  private BigDecimal averagePrice;
  private BigDecimal currentPrice;
  private BigDecimal evaluationAmount;
  private BigDecimal unrealizedProfit;
  private BigDecimal profitRate;
}
