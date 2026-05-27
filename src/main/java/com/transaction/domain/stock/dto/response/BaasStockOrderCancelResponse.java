package com.transaction.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockOrderCancelResponse {

  private Long orderId;
  private String status;
  private Integer cancelledQuantity;
  private Integer filledQuantity;
  private Integer remainingQuantity;
  private LocalDateTime cancelledAt;
}
