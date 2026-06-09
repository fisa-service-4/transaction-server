package com.transaction.domain.card.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaasCardItemResponse {

  private Long cardId;
  private String cardNumber;
  private Long linkedAccountId;
  private String cardStatus;
}
