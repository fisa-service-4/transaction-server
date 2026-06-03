# Todo — Saga 구현

> 참조: `docs/transaction-server-saga.md`
> 참조: `docs/onpremise-saga.md`
> 각 이슈는 독립 브랜치로 작업, 순서대로 진행

---

## Issue 1: feat(saga): Saga 인프라 — DB / Entity / 공통 서비스 / Kafka 설정

**브랜치:** `feat/#-saga-infrastructure`
**서버:** transaction-server

### 작업 목록

**schema.sql — 7개 테이블 추가**
- `saga_transaction`
- `saga_step_history`
- `idempotency_key`
- `outbox_event`
- `dead_letter_event`
- `reconciliation_result`
- `transaction_audit_log`

**Entity + Repository**
- `domain/saga/entity/SagaTransaction` + `SagaTransactionRepository`
- `domain/saga/entity/SagaStepHistory` + `SagaStepHistoryRepository`
- `domain/outbox/entity/OutboxEvent` + `OutboxEventRepository`
- `domain/deadletter/entity/DeadLetterEvent` + `DeadLetterEventRepository`
- `domain/reconciliation/entity/ReconciliationResult` + `ReconciliationResultRepository`
- `global/entity/IdempotencyKey` + `IdempotencyKeyRepository`
- `global/entity/TransactionAuditLog` + `TransactionAuditLogRepository`

**Enum**
- `domain/saga/enums/SagaStatus` — STARTED / PROCESSING / SUCCESS / FAILED / COMPENSATING / COMPENSATED / UNKNOWN / COMPENSATION_FAILED
- `domain/saga/enums/SagaStepStatus` — SUCCESS / FAILED / COMPENSATED
- `domain/saga/enums/SagaType` — BANK_TO_STOCK / STOCK_TO_BANK
- `domain/saga/enums/SagaStepName` — BANK_TRANSFER_REQUEST_CREATED / STOCK_CASH_DEPOSIT / BANK_TRANSFER_COMMIT / STOCK_CASH_WITHDRAW / STOCK_CASH_WITHDRAW_COMPENSATION / STOCK_CASH_DEPOSIT_COMPENSATION

**공통 서비스**
- `global/service/IdempotencyService` — check / complete / fail
- `global/service/AuditService` — record
- `domain/outbox/service/OutboxService` — save
- `domain/deadletter/service/DeadLetterService` — save

**Kafka 설정**
- `global/config/KafkaConfig` — `KafkaTemplate<String, String>` Producer 설정
- `global/config/KafkaTopics` — 토픽 상수 4개 (saga.started / saga.completed / saga.failed / saga.compensated)

### 완료 기준
- 앱 기동 시 7개 테이블 생성 확인 (schema.sql `continue-on-error: true`)
- `IdempotencyService.check()` → 신규 키 저장, 중복 키 감지 동작 확인

---

## Issue 2: feat(saga): SagaOrchestrator + 내부 라우팅 구현

**브랜치:** `feat/#-saga-orchestrator`
**서버:** transaction-server
**선행:** Issue 1 완료

### 작업 목록

**BankCoreClient 확장 — cancel API 추가**
- `cancelTransfer(transferId)` → `POST /internal/v1/bank/transfers/{id}/cancel`

**StockCoreClient 확장**
- `depositCash(accountId, idempotencyKey, request)` → `Idempotency-Key` 헤더 포함
- `withdrawCash(accountId, idempotencyKey, request)` → `Idempotency-Key` 헤더 포함
- 신규 DTO: `StockCashRequest` (`amount` 만), `StockCashResponse` (`accountId`, `cashBalance`)

**내부 라우팅 로직**
- 기존 `POST /baas/v1/bank/transfers` 핸들러 내부에 Saga 분기 추가
  - `fromAccountId` 계좌 유형 조회
  - 은행계좌 + 증권사 toBankCode → `SagaOrchestrator.bankToStock()`
  - 증권계좌 → `SagaOrchestrator.stockToBank()`
  - 그 외 → 기존 bank-to-bank 이체 유지

**SagaOrchestrator**
- `domain/saga/service/SagaOrchestrator`
  - `bankToStock(request, idempotencyKey, userId, traceId)`
  - `stockToBank(request, idempotencyKey, userId, traceId)`
  - BANK_TO_STOCK: Case A (STEP 2 실패 → cancel) / Case B (STEP 3 실패 → compensation) / UNKNOWN (timeout → 상태 조회 → 분기)
  - STOCK_TO_BANK: STEP 1 실패 (보상 없음) / STEP 2/3 실패 + UNKNOWN (stock deposit 보상)

**Idempotency-Key 생성 규칙 (SagaOrchestrator 내부)**

| 호출 대상 | 생성 키 |
|-----------|---------|
| BANK_TO_STOCK 정상 deposit | `{sagaId}_DEPOSIT` |
| STOCK_TO_BANK 정상 withdraw | `{sagaId}_WITHDRAW` |
| BANK_TO_STOCK 보상 withdraw | `{sagaId}_COMPENSATION_WITHDRAW` |
| STOCK_TO_BANK 보상 deposit | `{sagaId}_COMPENSATION_DEPOSIT` |

### 완료 기준
- 정상 흐름: `saga_transaction` SUCCESS, `saga_step_history` 3건 생성
- Case A (stock deposit 실패): `saga_transaction` FAILED, bank transfer CANCELLED, approve 미호출
- Case B (bank approve 실패): `saga_transaction` COMPENSATED, stock 예수금 원복
- UNKNOWN (approve timeout): `saga_transaction` UNKNOWN, `outbox_event`에 saga.unknown 저장
- 동일 `Idempotency-Key` 중복 요청 시 동일 응답 반환

---

## Issue 3: feat(saga): Outbox Relay + Reconciliation 구현

**브랜치:** `feat/#-saga-outbox-reconciliation`
**서버:** transaction-server
**선행:** Issue 2 완료

### 작업 목록

**OutboxRelayScheduler**
- `domain/outbox/scheduler/OutboxRelayScheduler`
  - `@Scheduled(fixedDelay = 1000)`
  - `published_yn = false` 이벤트 최대 100건 Kafka 발행
  - 성공 → `published_yn = true`, `published_at` 갱신
  - 3회 실패 → `DeadLetterService.save()` 호출

**ReconciliationService**
- `domain/reconciliation/service/ReconciliationService`
  - bank-server `POST /reconciliation/run` 호출
  - 결과를 `reconciliation_result` 테이블에 저장

### 완료 기준
- Outbox 이벤트 발행 후 Kafka UI에서 `saga.completed` 수신 확인
- 3회 실패 시 `dead_letter_event` 레코드 생성 확인
- `ReconciliationService` 호출 시 `reconciliation_result` 저장 확인
