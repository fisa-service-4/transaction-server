package com.transaction.global.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final ErrorResponse error;
  private final MetaResponse meta;

  private ApiResponse(boolean success, T data, ErrorResponse error, MetaResponse meta) {
    this.success = success;
    this.data = data;
    this.error = error;
    this.meta = meta;
  }

  public static <T> ApiResponse<T> success(T data, String traceId) {
    return new ApiResponse<>(true, data, null, MetaResponse.of(traceId));
  }

  public static <T> ApiResponse<T> fail(String code, String message, String traceId) {
    return new ApiResponse<>(false, null, ErrorResponse.of(code, message), MetaResponse.of(traceId));
  }
}
