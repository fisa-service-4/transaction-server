package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockOrderDetailResponse {

  private Long orderId;
  private Long accountId;
  private String stockCode;
  private String stockName;
  private String orderType;
  private String orderMethod;
  private Integer quantity;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private BigDecimal price;
  private BigDecimal averageExecutionPrice;
  private String status;
  private LocalDateTime orderedAt;
  private LocalDateTime updatedAt;
}
