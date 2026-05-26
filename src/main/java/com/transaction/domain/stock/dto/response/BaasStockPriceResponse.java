package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockPriceResponse {

  private String stockCode;
  private String stockName;
  private BigDecimal currentPrice;
  private BigDecimal changeRate;
  private LocalDateTime updatedAt;
}
