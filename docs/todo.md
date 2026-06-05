# Todo — Saga 구현

> 참조: `docs/transaction-server-sage.md`  
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
- `domain/saga/enums/SagaStatus` — STARTED / PROCESSING / SUCCESS / FAILED / COMPENSATING / COMPENSATED
- `domain/saga/enums/SagaStepStatus` — SUCCESS / FAILED / COMPENSATED
- `domain/saga/enums/SagaType` — BANK_TO_STOCK / STOCK_TO_BANK

**공통 서비스**
- `global/service/IdempotencyService` — check / complete / fail
- `global/service/AuditService` — record
- `domain/outbox/service/OutboxService` — save
- `domain/deadletter/service/DeadLetterService` — save

**Kafka 설정**
- `global/config/KafkaConfig` — `KafkaTemplate<String, String>` Producer 설정
- `global/config/KafkaTopics` — 토픽 상수 5개 (saga.started 등)

### 완료 기준
- 앱 기동 시 7개 테이블 생성 확인 (schema.sql `continue-on-error: true`)
- `IdempotencyService.check()` → 신규 키 저장, 중복 키 감지 동작 확인

---

## Issue 2: feat(saga): SagaOrchestrator + SagaController 구현

**브랜치:** `feat/#-saga-orchestrator`  
**서버:** transaction-server  
**선행:** Issue 1 완료

### 작업 목록

**StockCoreClient 확장**
- `depositCash(accountId, StockCashRequest)` 추가 → `POST /accounts/{id}/cash/deposit`
- `withdrawCash(accountId, StockCashRequest)` 추가 → `POST /accounts/{id}/cash/withdraw`
- 신규 DTO: `StockCashRequest` (`amount`, `sagaId`), `StockCashResponse` (`accountId`, `cashBalance`)

**SagaOrchestrator**
- `domain/saga/service/SagaOrchestrator`
  - `executeBankToStock(request, idempotencyKey, traceId)`
  - `executeStockToBank(request, idempotencyKey, traceId)`
  - Case A / Case B Compensation 로직 포함

**SagaController**
- `domain/saga/controller/SagaController`
  - `POST /baas/v1/saga/bank-to-stock`
  - `POST /baas/v1/saga/stock-to-bank`
  - `GET  /baas/v1/saga/{sagaId}`
  - `GET  /baas/v1/saga/{sagaId}/steps`
- 신규 DTO: `BankToStockRequest`, `StockToBankRequest`, `SagaResponse`, `SagaDetailResponse`

### 완료 기준
- 정상 흐름: `saga_transaction` SUCCESS, `saga_step_history` 3건 생성
- Case A (stock deposit 실패): `saga_transaction` FAILED, bank 잔액 유지, approve 미호출
- Case B (bank approve 실패): `saga_transaction` COMPENSATED, stock 예수금 원복
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
  - Case A에서 방치된 `REQUESTED` 이체 탐지 및 로깅 처리

### 완료 기준
- Outbox 이벤트 발행 후 Kafka UI에서 `saga.completed` 수신 확인
- 3회 실패 시 `dead_letter_event` 레코드 생성 확인
- `ReconciliationService` 호출 시 `reconciliation_result` 저장 확인
