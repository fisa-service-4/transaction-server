package com.transaction.global.response;

import lombok.Getter;

@Getter
public class MetaResponse {

  private final String traceId;

  private MetaResponse(String traceId) {
    this.traceId = traceId;
  }

  public static MetaResponse of(String traceId) {
    return new MetaResponse(traceId);
  }
}
