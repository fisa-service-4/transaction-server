# Unit Test Plan — transaction-server

## 개요

- 테스트 방식: Mockito 기반 단위 테스트 (`@ExtendWith(MockitoExtension.class)`)
- Spring Context 사용 안 함 (단순 구현체 + Mock)
- 총 32개 케이스, 6개 클래스
- 실행 순서: Idempotency → Saga B→S → Saga S→B → Outbox → BaasTransfer → Reconciliation

---

## 클래스별 테스트 계획

### 1. IdempotencyServiceTest (6개)

대상: `com.transaction.global.service.IdempotencyService`

| # | 메서드명 | 시나리오 | 핵심 검증 |
|---|---------|---------|---------|
| 1 | `check_신규키_빈값반환및PROCESSING저장` | findById empty → save 호출 | Optional.empty() 반환, save 1회 호출 |
| 2 | `check_PROCESSING키_예외발생` | PROCESSING 상태 키 존재 | DuplicateRequestInProgressException throw |
| 3 | `check_SUCCESS키_캐시응답반환` | SUCCESS 상태 키 존재 | Optional.of(payload) 반환, save 미호출 |
| 4 | `shouldAllowRetryWhenPreviousRequestFailed` | FAILED 상태 키 존재 | Optional.empty() 반환(재처리 허용), save 미호출 |
| 5 | `complete_SUCCESS상태로전이및응답저장` | 기존 키에 complete 호출 | status=SUCCESS, responsePayload 저장 |
| 6 | `fail_FAILED상태로전이` | 기존 키에 fail 호출 | status=FAILED |

**FAILED 정책 주의사항**:
- FAILED 키는 `isProcessing()` = false, `getResponsePayload()` = null
- `check()` 진입 시 `Optional.empty()` 반환 → 호출자가 신규 요청으로 처리 (재시도 허용)
- save는 호출되지 않음 (check 분기에서 early return)

---

### 2. SagaOrchestratorTest — BANK_TO_STOCK (8개)

대상: `com.transaction.domain.saga.service.SagaOrchestrator#bankToStock`

| # | 메서드명 | 시나리오 | 핵심 검증 |
|---|---------|---------|---------|
| 1 | `bankToStock_정상흐름_SUCCESS` | 3 step 모두 성공 | saga SUCCESS, auditService.record 호출 |
| 2 | `bankToStock_STEP1실패_FAILED_bank_cancel미호출` | createTransfer 예외 | failSaga 호출, cancelTransfer 미호출 (transferId=null이므로 tryCancelTransfer early-return) |
| 3 | `bankToStock_STEP2실패_CaseA_FAILED` | depositCash 예외 | failSaga 호출, cancelTransfer 호출 |
| 4 | `bankToStock_STEP3실패후bank상태SUCCESS_복구` | approve 예외 → getTransfer SUCCESS | completeSaga 호출 (보상 없음) |
| 5 | `bankToStock_STEP3실패후bank상태REQUESTED_CaseB_COMPENSATED` | approve 예외 → getTransfer REQUESTED | withdrawCash 호출, compensatedSaga 호출 |
| 6 | `bankToStock_STEP3실패후상태조회도실패_UNKNOWN` | approve 예외 → getTransfer도 예외 | unknownSaga 호출 |
| 7 | `bankToStock_보상실패_COMPENSATION_FAILED` | Case B + withdrawCash도 예외 | compensationFailedSaga 호출 |
| 8 | `bankToStock_중복요청_캐시응답반환` | idempotencyService.check 캐시 hit | bankCoreClient 미호출 |

---

### 3. SagaOrchestratorTest — STOCK_TO_BANK (4개)

대상: `com.transaction.domain.saga.service.SagaOrchestrator#stockToBank`

| # | 메서드명 | 시나리오 | 핵심 검증 |
|---|---------|---------|---------|
| 1 | `stockToBank_정상흐름_SUCCESS` | 3 step 모두 성공 | saga SUCCESS |
| 2 | `stockToBank_STEP1실패_FAILED_보상없음` | withdrawCash 예외 | failSaga 호출, depositCash 미호출 |
| 3 | `stockToBank_STEP2or3실패_보상성공_COMPENSATED` | bank 처리 실패 → depositCash 보상 성공 | compensatedSaga 호출 |
| 4 | `stockToBank_보상실패_COMPENSATION_FAILED` | bank 실패 + depositCash도 예외 | compensationFailedSaga 호출 |

---

### 4. OutboxRelaySchedulerTest (4개)

대상: `com.transaction.domain.outbox.scheduler.OutboxRelayScheduler`

| # | 메서드명 | 시나리오 | 핵심 검증 |
|---|---------|---------|---------|
| 1 | `relay_pending없음_아무동작안함` | findPending 빈 리스트 | kafkaTemplate.send 미호출 |
| 2 | `relay_발행성공_markPublished호출` | kafkaTemplate.send 성공 | markPublished 호출, DLQ 미저장 |
| 3 | `relay_1회실패_DLQ미이관` | kafka 예외, retryCount 1 | incrementRetry 호출, deadLetterService.save 미호출 |
| 4 | `relay_3회실패_DLQ이관` | kafka 예외, retryCount 3 | deadLetterService.save 호출, markPublished 호출 |

---

### 5. BaasTransferServiceTest (7개)

대상: `com.transaction.domain.bank.service.BaasTransferService#createTransfer`

| # | 메서드명 | 시나리오 | 핵심 검증 |
|---|---------|---------|---------|
| 1 | `createTransfer_BankToBank_직접이체` | fromIsStock=false, toIsStock=false | bankCoreClient.createTransfer 직접 호출 |
| 2 | `createTransfer_BankToStock_Saga위임` | fromIsStock=false, toIsStock=true | sagaOrchestrator.bankToStock 호출 |
| 3 | `createTransfer_StockToBank_Saga위임` | fromIsStock=true, toIsStock=false | sagaOrchestrator.stockToBank 호출 |
| 4 | `createTransfer_StockToStock_예외` | fromIsStock=true, toIsStock=true | SagaException throw |
| 5 | `createTransfer_계좌validate실패_예외` | validateAccount validYn=false | SagaException throw |
| 6 | `createTransfer_정산계좌미설정_예외` | settlementAccountId null | SagaException throw |
| 7 | `createTransfer_자기자신이체_동작확인` | fromAccountId == toAccountId (bank-to-bank) | 현재 코드가 막지 않음 확인 (통과 → 버그 후보) |

---

### 6. ReconciliationServiceTest (3개)

대상: `com.transaction.domain.reconciliation.service.ReconciliationService`

| # | 메서드명 | 시나리오 | 핵심 검증 |
|---|---------|---------|---------|
| 1 | `check_이상없음_SUCCESS` | 4개 카운트 모두 0 | status=SUCCESS, mismatchCount=0 |
| 2 | `check_COMPENSATION_FAILED존재_카운트분리` | compensationFailedCount=2, 나머지 0 | mismatchCount=2, status=FAILED |
| 3 | `check_복합이상_각카운트독립집계` | 4개 카운트 각각 1,2,3,4 | mismatch=10, status=FAILED |

---

## 일정

| 일차 | 작업 | 커밋 |
|-----|------|-----|
| Day 1 | IdempotencyServiceTest (6개) | 커밋 1 |
| Day 1-2 | SagaOrchestratorTest B→S (8개) | 커밋 2 |
| Day 2 | SagaOrchestratorTest S→B (4개) | 커밋 3 |
| Day 3 | OutboxRelaySchedulerTest (4개) | 커밋 4 |
| Day 3 | BaasTransferServiceTest (7개) | 커밋 5 |
| Day 4 | ReconciliationServiceTest (3개) | 커밋 6 |

---

## Mock 전략

| 구분 | 전략 |
|-----|-----|
| Repository | `@Mock` |
| Feign Client | `@Mock` |
| SagaStateManager | `@Mock` |
| IdempotencyService | `@Mock` |
| AuditService | `@Mock` |
| ObjectMapper | `new ObjectMapper().registerModule(new JavaTimeModule())` (실제 인스턴스) |
| SagaTransaction | `mock(SagaTransaction.class)` + `when(saga.getSagaId()).thenReturn(1L)` |
| DTO (노 생성자) | `mock(ClassName.class)` |
