package com.transaction;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("외부 인프라(Oracle DB, Redis, Kafka) 연결 필요 — 로컬 단독 실행 불가")
class TransactionServerApplicationTests {

  @Test
  void contextLoads() {}
}
