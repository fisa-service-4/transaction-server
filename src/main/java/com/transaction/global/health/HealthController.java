package com.transaction.global.health;

import com.transaction.global.response.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/baas/v1")
public class HealthController {

  @GetMapping("/health")
  public ApiResponse<Map<String, String>> health() {
    return ApiResponse.success(Map.of("status", "UP"), UUID.randomUUID().toString());
  }
}
