package com.transaction.global.health;

import com.transaction.domain.bank.client.BankCoreClient;
import com.transaction.domain.stock.client.StockCoreClient;
import com.transaction.global.response.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/baas/v1")
public class HealthController {

  private final BankCoreClient bankCoreClient;
  private final StockCoreClient stockCoreClient;

  public HealthController(BankCoreClient bankCoreClient, StockCoreClient stockCoreClient) {
    this.bankCoreClient = bankCoreClient;
    this.stockCoreClient = stockCoreClient;
  }

  @GetMapping("/health")
  public ApiResponse<Map<String, String>> health() {
    return ApiResponse.success(Map.of("status", "UP"), "health");
  }

  @GetMapping("/health/bank")
  public ResponseEntity<ApiResponse<Map<String, String>>> bankHealth() {
    try {
      bankCoreClient.checkHealth();
      return ResponseEntity.ok(
          ApiResponse.success(Map.of("status", "UP"), UUID.randomUUID().toString()));
    } catch (Exception e) {
      return ResponseEntity.status(503)
          .body(ApiResponse.success(Map.of("status", "DOWN"), UUID.randomUUID().toString()));
    }
  }

  @GetMapping("/health/stock")
  public ResponseEntity<ApiResponse<Map<String, String>>> stockHealth() {
    try {
      stockCoreClient.checkHealth();
      return ResponseEntity.ok(
          ApiResponse.success(Map.of("status", "UP"), UUID.randomUUID().toString()));
    } catch (Exception e) {
      return ResponseEntity.status(503)
          .body(ApiResponse.success(Map.of("status", "DOWN"), UUID.randomUUID().toString()));
    }
  }
}
