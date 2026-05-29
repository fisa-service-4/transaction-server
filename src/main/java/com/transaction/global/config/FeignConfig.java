package com.transaction.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.global.exception.BankCoreException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

public class FeignConfig {

  @Bean
  public ErrorDecoder errorDecoder() {
    return new BankCoreErrorDecoder();
  }

  @Slf4j
  static class BankCoreErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
      HttpStatus status = HttpStatus.valueOf(response.status());
      String code = "BANK_CORE_ERROR";
      String message = "bank-server 오류가 발생했습니다.";

      try (InputStream body = response.body().asInputStream()) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode error = root.path("error");
        if (!error.isMissingNode()) {
          code = error.path("code").asText(code);
          message = error.path("message").asText(message);
        }
      } catch (IOException e) {
        log.warn("[BankCoreErrorDecoder] 응답 바디 파싱 실패: method={}, status={}", methodKey, status, e);
      }

      log.error(
          "[BankCoreErrorDecoder] bank-server 오류: method={}, status={}, code={}, message={}",
          methodKey,
          status,
          code,
          message);

      return new BankCoreException(code, message, status);
    }
  }
}
