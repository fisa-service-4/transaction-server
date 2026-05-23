package com.transaction.domain.bank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class BaasTransferRequest {

  @NotNull private Long fromAccountId;

  @NotBlank private String toBankCode;

  @NotBlank private String toAccountNumber;

  @NotNull @Positive private BigDecimal transferAmount;

  @NotBlank private String requestedBy;
}
