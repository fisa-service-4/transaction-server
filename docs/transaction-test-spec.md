# transaction-server 테스트 명세서

> 작성일: 2026-06-15
> 테스트 프레임워크: JUnit 5 + Mockito
> 총 테스트: **53개** (단위 52 실행 · 0 실패, 통합 1 스킵)

---

## 1. 테스트 전략

| 항목 | 내용 |
|------|------|
| 테스트 종류 | 단위 테스트 (Unit Test) + Controller Slice Test |
| Mock 방식 (Service) | `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks` |
| Mock 방식 (Controller) | `@WebMvcTest` + `@MockBean` |
| 검증 방식 | AssertJ (`assertThat`, `assertThatThrownBy`) + Mockito verify + MockMvc (`jsonPath`, `status`) |
| 대상 레이어 | Service / Scheduler / Controller (Repository는 Mock 처리) |

### 외부 의존성 처리

- **BankCoreClient**, **StockCoreClient**: Feign Client — `@Mock`으로 대체
- **KafkaTemplate**: `@Mock`으로 대체, `CompletableFuture` 성공/실패 stub
- **Repository** 전체: `@Mock`으로 대체
- **TransactionServerApplicationTests**: 외부 인프라(Oracle DB, Redis, Kafka) 연결 필요 → `@Disabled` 처리
- **DTO 필드 주입**: `@Getter` + `@NoArgsConstructor` 전용 DTO는 `ReflectionTestUtils.setField()`로 필드 세팅

---

## 2. 테스트 클래스별 명세

### 2-1. BaasTransferServiceTest

> `com.transaction.domain.bank.service.BaasTransferService`
> 이체 요청 라우팅 로직 검증 (Bank↔Bank / Bank→Stock / Stock→Bank)

**Mocked 의존성**

| 의존성 | 역할 |
|--------|------|
| BankCoreClient | 계좌 유효성 검증, 이체 생성 |
| StockCoreClient | 증권 계좌 유효성 검증, 계좌 목록 조회 |
| UserResolver | accountId → xUserId 변환 |
| UserAccountMappingRepository | 증권 계좌 여부(STOCK) 판별 |
| SagaOrchestrator | Saga 위임 여부 검증 |
| BrokerCodeProperties | 증권사 코드 및 정산 계좌 설정 |

**테스트 케이스 (6개)**

| 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|-------------|
| `createTransfer_BankToBank_직접이체` | 은행→은행 이체 | `bankCoreClient.createTransfer` 호출, Saga 미호출 |
| `createTransfer_BankToStock_Saga위임` | 은행→증권 이체 | `sagaOrchestrator.bankToStock` 위임, bank 직접 이체 미호출 |
| `createTransfer_StockToBank_Saga위임` | 증권→은행 이체 | `sagaOrchestrator.stockToBank` 위임, 정산 계좌 활용 |
| `createTransfer_StockToStock_예외` | 증권→증권 이체 | `SagaException` 발생, Saga 미호출 |
| `createTransfer_계좌validate실패_예외` | 대상 계좌 유효성 실패 | `SagaException` 발생, 이체 미생성 |
| `createTransfer_정산계좌미설정_예외` | 증권사 정산 계좌 null | `SagaException` 발생, Saga 미호출 |

---

### 2-2. SagaOrchestratorBankToStockTest

> `com.transaction.domain.saga.service.SagaOrchestrator.bankToStock()`
> BANK_TO_STOCK Saga 3단계 흐름 전 케이스 검증

**Saga 흐름**

```
STEP 1: bank 이체 레코드 생성 (잔액 변동 없음)
STEP 2: stock 예수금 충전 (reversible 선행 실행)
STEP 3: bank 이체 확정 (approve → 실제 출금)
```

**실패 시나리오 설계**

| Case | 트리거 | 보상 |
|------|--------|------|
| STEP1 실패 | bank createTransfer 예외 | 없음 (잔액 변동 없으므로) |
| STEP2 실패 (Case A) | stock depositCash 예외 | bank transfer cancel |
| STEP3 실패 + bank SUCCESS | approve 예외 + getTransfer=SUCCESS | 복구 (SUCCESS 확정) |
| STEP3 실패 + bank REQUESTED (Case B) | approve 예외 + getTransfer=REQUESTED | stock withdrawCash 보상 |
| STEP3 실패 + 상태 조회 실패 | approve 예외 + getTransfer 예외 | UNKNOWN (수동 개입) |
| 보상 실패 | Case B + stock withdraw 예외 | COMPENSATION_FAILED |
| 중복 요청 | idempotencyService 캐시 적중 | Saga 초기화 없이 캐시 반환 |

**테스트 케이스 (8개)**

| 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|-------------|
| `bankToStock_정상흐름_SUCCESS` | 전 단계 성공 | `completeSaga` 호출, audit 기록 |
| `bankToStock_STEP1실패_FAILED_bank_cancel미호출` | STEP1 bank 예외 | `failSaga` 호출, `cancelTransfer` 미호출 |
| `bankToStock_STEP2실패_CaseA_FAILED` | STEP2 stock 예외 | `failSaga` 호출, `cancelTransfer` 호출 |
| `bankToStock_STEP3실패후bank상태SUCCESS_복구` | approve timeout + bank SUCCESS | `completeSaga` 호출, stock withdraw 미호출 |
| `bankToStock_STEP3실패후bank상태REQUESTED_CaseB_COMPENSATED` | approve 실패 + bank REQUESTED | `stock.withdrawCash` 호출, `compensatedSaga` 호출 |
| `bankToStock_STEP3실패후상태조회도실패_UNKNOWN` | approve 예외 + getTransfer 예외 | `unknownSaga` 호출 |
| `bankToStock_보상실패_COMPENSATION_FAILED` | Case B + withdraw 예외 | `compensationFailedSaga` 호출 |
| `bankToStock_중복요청_캐시응답반환` | idempotency 캐시 적중 | `initSaga` 미호출, 캐시 응답 반환 |

---

### 2-3. SagaOrchestratorStockToBankTest

> `com.transaction.domain.saga.service.SagaOrchestrator.stockToBank()`
> STOCK_TO_BANK Saga 3단계 흐름 전 케이스 검증

**Saga 흐름**

```
STEP 1: stock 예수금 출금
STEP 2: bank 이체 레코드 생성 (정산 계좌 fromAccountId 사용)
STEP 3: bank 이체 확정 (approve)
```

**실패 시나리오 설계**

| Case | 트리거 | 보상 |
|------|--------|------|
| STEP1 실패 | stock withdrawCash 예외 | 없음 (stock 변동 없으므로) |
| STEP2/3 실패 | bank 예외 | stock depositCash 보상 |
| STEP3 실패 + bank SUCCESS | approve 예외 + getTransfer=SUCCESS | 복구 (SUCCESS 확정) |
| STEP3 실패 + bank REQUESTED | approve 예외 + getTransfer=REQUESTED | stock depositCash 보상 |
| STEP3 실패 + 상태 조회 실패 | approve 예외 + getTransfer 예외 | UNKNOWN (수동 개입) |
| 보상 실패 | STEP2/3 실패 + deposit 예외 | COMPENSATION_FAILED |
| 중복 요청 | idempotencyService 캐시 적중 | Saga 초기화 없이 캐시 반환 |

**테스트 케이스 (8개)**

| 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|-------------|
| `stockToBank_정상흐름_SUCCESS` | 전 단계 성공 | `completeSaga` 호출, audit 기록 |
| `stockToBank_STEP1실패_FAILED_보상없음` | STEP1 stock 예외 | `failSaga` 호출, bank 미호출 |
| `stockToBank_STEP2실패_보상성공_COMPENSATED` | STEP2 bank 예외 | `depositCash` 보상 호출, `compensatedSaga` 호출 |
| `stockToBank_보상실패_COMPENSATION_FAILED` | STEP2 실패 + deposit 예외 | `compensationFailedSaga` 호출 |
| `stockToBank_STEP3실패후bank상태SUCCESS_복구` | approve timeout + bank SUCCESS | `completeSaga` 호출, deposit 미호출 |
| `stockToBank_STEP3실패후bank상태REQUESTED_보상_COMPENSATED` | approve 실패 + bank REQUESTED | `depositCash` 보상 호출, `compensatedSaga` 호출 |
| `stockToBank_STEP3실패후상태조회도실패_UNKNOWN` | approve 예외 + getTransfer 예외 | `unknownSaga` 호출 |
| `stockToBank_중복요청_캐시응답반환` | idempotency 캐시 적중 | `initSaga` 미호출, `withdrawCash` 미호출 |

---

### 2-4. OutboxRelaySchedulerTest

> `com.transaction.domain.outbox.scheduler.OutboxRelayScheduler`
> Transactional Outbox 패턴의 Kafka 발행 및 DLQ 이관 로직 검증

**테스트 케이스 (4개)**

| 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|-------------|
| `relay_pending없음_아무동작안함` | Pending 이벤트 없음 | Kafka send 미호출, markPublished 미호출 |
| `relay_발행성공_markPublished호출` | Kafka 발행 성공 | `markPublished` 호출, DLQ 저장 미호출 |
| `relay_1회실패_DLQ미이관` | retryCount=1 (MAX=3 미도달) | `incrementRetry` 호출, DLQ 미이관 |
| `relay_3회실패_DLQ이관` | retryCount=3 (MAX 도달) | `deadLetterService.save` 호출, `markPublished` 호출 |

**핵심 검증 로직**

- Kafka `CompletableFuture.get()`의 성공/실패를 `stubKafkaSuccess` / `stubKafkaFail`로 분리 stub
- DLQ 이관 임계값: `retryCount >= 3`
- DLQ 이관 후 `markPublished` 호출로 무한 재시도 방지

---

### 2-5. ReconciliationServiceTest

> `com.transaction.domain.reconciliation.service.ReconciliationService`
> Saga 이상 상태 집계 및 결과 저장 로직 검증

**감지 대상 이상 상태 (4종)**

| 상태 | 설명 |
|------|------|
| `COMPENSATION_FAILED` | 보상 트랜잭션 실패 — 즉각 수동 개입 필요 |
| `UNKNOWN` | approve timeout 후 상태 불명 — 수동 확인 필요 |
| `PROCESSING` (stuck) | 설정된 timeout 초과 후 처리 중 상태 지속 |
| `COMPENSATING` (stuck) | 설정된 timeout 초과 후 보상 중 상태 지속 |

**테스트 케이스 (3개)**

| 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|-------------|
| `check_이상없음_SUCCESS` | 모든 카운트 0 | `status="SUCCESS"`, `mismatchCount=0` |
| `check_COMPENSATION_FAILED존재_카운트분리` | compensationFailed=2 | `status="FAILED"`, `compensationFailedCount=2` |
| `check_복합이상_각카운트독립집계` | 4종 혼합 이상 | mismatchCount = 합산값, 각 카운트 독립 집계 |

---

### 2-6. IdempotencyServiceTest

> `com.transaction.global.service.IdempotencyService`
> 중복 요청 방지 키 상태 머신 검증

**상태 전이**

```
[없음] → PROCESSING → SUCCESS (complete 호출)
                    → FAILED  (fail 호출)
```

**테스트 케이스 (6개)**

| 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|-------------|
| `check_신규키_빈값반환및PROCESSING저장` | 최초 요청 | `Optional.empty()` 반환, PROCESSING 저장 |
| `check_PROCESSING키_예외발생` | 동일 키 처리 중 재요청 | `DuplicateRequestInProgressException` |
| `check_SUCCESS키_캐시응답반환` | 완료된 요청 재요청 | `Optional.of(responsePayload)` 반환 |
| `shouldAllowRetryWhenPreviousRequestFailed` | FAILED 키 재요청 | `Optional.empty()` 반환 (재시도 허용), save 미호출 |
| `complete_SUCCESS상태로전이및응답저장` | 처리 완료 시 | `isCompleted()=true`, payload 저장 |
| `fail_FAILED상태로전이` | 처리 실패 시 | `isProcessing()=false`, `isCompleted()=false` |

---

### 2-7. BaasCardServiceTest

> `com.transaction.domain.card.service.BaasCardService`
> Card API 포워딩 로직 검증 (BankCoreClient 호출 및 예외 전파)

**Mocked 의존성**

| 의존성 | 역할 |
|--------|------|
| BankCoreClient | Card / CardApproval API 호출 |
| UserResolver | accountId → xUserId 변환 (카드 목록 조회 시) |

**테스트 케이스 (8개)**

| 클래스명 | 메서드명 | 시나리오 | 검증 포인트 |
|----------|----------|----------|-------------|
| `GetCardsByAccount` | `success` | 정상 조회 — 카드 1건 반환 | `userResolver.resolveByAccount("BANK")` 호출, `content` 사이즈 1 |
| `GetCardsByAccount` | `emptyCards` | 연결 카드 없는 계좌 | `content` 빈 목록 |
| `GetCardsByAccount` | `userMappingNotFound` | 계좌 매핑 없음 | `UserMappingNotFoundException` 전파 |
| `GetCardsByAccount` | `bankCoreException` | bank-server ACCOUNT_001 | `BankCoreException` 전파 |
| `GetCardApprovals` | `success` | 필터 없이 승인 내역 조회 | `xUserId=0L` 고정, `merchantName` 검증 |
| `GetCardApprovals` | `withFilters` | 날짜·상태·카테고리 필터 | 정확한 파라미터 매칭(`eq(...)`) |
| `GetCardApprovals` | `cardNotFound` | 존재하지 않는 cardId | `BankCoreException(CARD_001)` 전파 |
| `GetCardApprovals` | `notOwnCard` | 본인 카드 아님 | `BankCoreException(CARD_002)` 전파 |

**주요 구현 특이사항**

- `getCardApprovals`는 `xUserId = 0L` 고정 — `UserResolver` 호출 없음
- `ReflectionTestUtils.setField()`로 `@NoArgsConstructor` 전용 DTO 필드 세팅

---

### 2-8. BaasCardControllerTest

> `com.transaction.domain.card.controller.BaasCardController`
> HTTP 요청 직렬화·역직렬화 및 예외 응답 포맷 검증
> `GlobalExceptionHandler`는 `@RestControllerAdvice`로 `@WebMvcTest`에 자동 로드됨

**Mocked 의존성**

| 의존성 | 역할 |
|--------|------|
| BaasCardService | `@MockBean`으로 대체 |

**테스트 케이스 (9개)**

| 클래스명 | 메서드명 | 시나리오 | HTTP | 검증 포인트 |
|----------|----------|----------|------|-------------|
| `GetCardsByAccount` | `success` | 카드 1건 | 200 | `$.data.content[0].cardId`, `cardNumber`, `cardStatus` |
| `GetCardsByAccount` | `emptyCards` | 연결 카드 없음 | 200 | `$.data.content` 빈 배열 |
| `GetCardsByAccount` | `userMappingNotFound` | 매핑 없음 | 500 | `$.error.code = MAPPING_001` |
| `GetCardsByAccount` | `bankCoreAccountNotFound` | bank-server ACCOUNT_001 | 404 | `$.error.code = ACCOUNT_001` |
| `GetCardApprovals` | `success` | 승인 내역 1건 | 200 | `approvalId`, `merchantName`, `approvalAmount`, `totalElements` |
| `GetCardApprovals` | `withQueryParams` | 날짜·상태·카테고리 파라미터 | 200 | `$.data.content` 빈 배열, `totalElements=0` |
| `GetCardApprovals` | `cardNotFound` | 카드 없음 | 404 | `$.error.code = CARD_001` |
| `GetCardApprovals` | `notOwnCard` | 본인 카드 아님 | 403 | `$.error.code = CARD_002` |
| `GetCardApprovals` | `approvalNotFound` | 승인 내역 없음 | 404 | `$.error.code = CARD_003` |

**엔드포인트**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/baas/v1/card/accounts/{accountId}/cards` | 계좌별 카드 목록 조회 |
| GET | `/baas/v1/card/cards/{cardId}/approvals` | 카드 승인 내역 조회 (쿼리 파라미터: `fromDate`, `toDate`, `approvalStatus`, `merchantCategory`, `page`, `size`) |

---

## 3. 전체 테스트 결과 요약

| 테스트 클래스 | 테스트 수 | 성공 | 실패 | 스킵 |
|---------------|-----------|------|------|------|
| BaasTransferServiceTest | 6 | 6 | 0 | 0 |
| SagaOrchestratorBankToStockTest | 8 | 8 | 0 | 0 |
| SagaOrchestratorStockToBankTest | 8 | 8 | 0 | 0 |
| OutboxRelaySchedulerTest | 4 | 4 | 0 | 0 |
| ReconciliationServiceTest | 3 | 3 | 0 | 0 |
| IdempotencyServiceTest | 6 | 6 | 0 | 0 |
| BaasCardServiceTest | 8 | 8 | 0 | 0 |
| BaasCardControllerTest | 9 | 9 | 0 | 0 |
| TransactionServerApplicationTests | 1 | 0 | 0 | 1 |
| **합계** | **53** | **52** | **0** | **1** |

---

## 4. 미테스트 영역

| 영역 | 이유 | 비고 |
|------|------|------|
| `BaasTransferService.approveTransfer` / `getTransfer` | Bank-to-Bank 이체의 2·3단계로 단순 위임 로직 | 별도 오케스트레이션 없음 |
| `SagaStateManager` | 내부 Transactional 분리 및 Outbox 이벤트 저장 통합 | 통합 테스트 필요 (DB + Kafka 필요) |
| `ReconciliationScheduler` | `@Scheduled` + `ReconciliationService.check()` 단순 위임 | `ReconciliationServiceTest`에서 로직 검증 완료 |
| `TransactionServerApplicationTests (contextLoads)` | 외부 인프라(Oracle DB, Redis, Kafka) 연결 필요 | Docker Compose 환경에서만 실행 가능 |

---

## 5. 보완 이력

| 일자 | 내용 |
|------|------|
| 2026-06-15 | `SagaOrchestratorStockToBankTest`에 STEP3 timeout 시나리오 3개, 중복요청 1개 추가 (기존 4개 → 8개) |
| 2026-06-15 | `TransactionServerApplicationTests` `@Disabled` 처리 (인프라 미연결 시 빌드 실패 방지) |
| 2026-06-15 | `BaasCardServiceTest` (8개) · `BaasCardControllerTest` (9개) 추가 — Card API 포워딩 및 HTTP 응답 검증 |
