package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockOrderItemResponse {

  private Long orderId;
  private String stockCode;
  private String stockName;
  private String orderType;
  private String orderMethod;
  private Integer quantity;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private BigDecimal price;
  private String status;
  private LocalDateTime orderedAt;
}
