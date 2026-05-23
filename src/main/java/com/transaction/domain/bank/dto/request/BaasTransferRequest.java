package com.transaction.domain.bank.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class BaasTransferRequest {

  @NotNull private Long fromAccountId;

  @NotBlank private String toBankCode;

  @NotBlank private String toAccountNumber;

  @NotNull
  @DecimalMin(value = "0", exclusive = true)
  private BigDecimal transferAmount;

  @NotBlank private String requestedBy;
}
