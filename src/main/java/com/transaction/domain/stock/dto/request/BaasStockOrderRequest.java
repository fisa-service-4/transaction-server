package com.transaction.domain.stock.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasStockOrderRequest {

  @NotBlank private String stockCode;

  @NotBlank private String orderType;

  @NotBlank private String orderMethod;

  @NotNull
  @Min(1)
  private Integer quantity;

  private Integer price;
}
