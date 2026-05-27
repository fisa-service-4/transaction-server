package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockExecutionResponse {

  private Long executionId;
  private Long orderId;
  private String stockCode;
  private String stockName;
  private BigDecimal executedPrice;
  private Integer executedQuantity;
  private BigDecimal executionAmount;
  private LocalDateTime executedAt;
}
