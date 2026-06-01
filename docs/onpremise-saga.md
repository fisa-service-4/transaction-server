# Saga 설계 문서

> 관련 서버: `transaction-server` · `bank-server` · `stock-server`  
> 최종 수정: 2026-06-01

---

# 1. 역할 분담

```text
frontend
    ↓
service-backend
    ↓
transaction-server  ← Saga Orchestrator
    ↓                       ↓
bank-server          stock-server
```

| 서버 | Saga 역할 |
|------|-----------|
| transaction-server | Saga 생성·관리, 각 서버 호출 조율, Compensation 처리, Outbox → Kafka 발행 |
| bank-server | 이체 Reserve/Commit API 제공 (기존 2-step 이체 플로우) |
| stock-server | 예수금 입출금 API 제공 (신규 구현 필요) |

**원칙:**
- service-backend는 transaction-server만 호출한다
- bank-server / stock-server는 transaction-server 내부에서만 호출한다
- Core 서버 직접 접근 금지

---

# 2. Kafka 사용 범위

Kafka는 **서로 다른 금융 원장 간 이동**에만 사용한다.

| 사용 O | 사용 X |
|--------|--------|
| 은행 계좌 → 증권 예수금 충전 | 은행 내부 이체 |
| 증권 예수금 → 은행 계좌 환불 | 증권 내부 주문/체결 |
| | 단일 DB 내부 거래 |

---

# 3. 핵심 전략

## 3-1. Saga Pattern

분산 트랜잭션 Orchestration. transaction-server가 각 단계를 순서대로 호출하고 실패 시 Compensation을 수행한다.

담당 테이블: `SAGA_TRANSACTION`, `SAGA_STEP_HISTORY`

## 3-2. Transactional Outbox

transaction-server 내 Saga 이벤트 발행 보장.

```text
Saga 상태 변경
+ OUTBOX_EVENT 저장
→ 동일 트랜잭션 Commit
→ OutboxRelayScheduler → Kafka Publish
```

담당 테이블: `OUTBOX_EVENT` (각 서버별 자체 관리)  
담당 서버: **transaction-server**, **bank-server**, **stock-server** 각각 구현

## 3-3. Idempotency Key

중복 Saga 요청 방지. 동일 `Idempotency-Key`로 재요청 시 저장된 응답 반환.

담당 테이블: `IDEMPOTENCY_KEY`

## 3-4. Reconciliation

배치 기반 원장 정합성 검증. Saga Case A(bank 미커밋 이체 방치) 후처리 포함.

담당 테이블: `RECONCILIATION_RESULT`

## 3-5. Audit Log

모든 Saga 상태 변화 기록.

담당 테이블: `TRANSACTION_AUDIT_LOG`

## 3-6. Dead Letter

Outbox Relay 3회 실패 이벤트 저장.

담당 테이블: `DEAD_LETTER_EVENT`

---

# 4. 서버별 구현 내용

## 4-1. bank-server

**기존 2-step 이체 API를 Saga Reserve/Commit으로 활용한다. 신규 API 개발 불필요.**

| 단계 | API | 동작 |
|------|-----|------|
| Reserve | `POST /internal/v1/bank/transfers` | 이체 예약 → `REQUESTED` 반환 **(잔액 변동 없음)** |
| Commit | `POST /internal/v1/bank/transfers/{id}/approve` | 실제 출금 실행 → `SUCCESS` 반환 |

- `POST /transfers` Request: `{ fromAccountId, toBankCode, toAccountNumber, transferAmount, requestedBy }`
- Commit은 transaction-server가 Saga 흐름 내에서 호출한다
- Cancel API 없음 → approve 미호출 시 `REQUESTED` 상태 방치, Reconciliation 배치로 정리

## 4-2. stock-server

**Saga 전용 예수금 입출금 API 신규 구현 필요.**  
API spec: `docs/api/api-stock-server.md` — `STOCK-CASH-001`, `STOCK-CASH-002`

| API | 역할 |
|-----|------|
| `POST /internal/v1/stock/accounts/{accountId}/cash/deposit` | 예수금 충전 (Saga 정상 step) |
| `POST /internal/v1/stock/accounts/{accountId}/cash/withdraw` | 예수금 출금 (Compensation) |

Request (공통): `{ "amount": 500000, "sagaId": 1001 }`  
Response (공통): `{ "accountId": 2001, "cashBalance": 10500000 }`

## 4-3. transaction-server

신규 구현 대상 전체.

**도메인 패키지:**

| 패키지 | 구현 내용 |
|--------|-----------|
| `domain/saga` | SagaTransaction Entity/Repo, SagaStepHistory Entity/Repo, SagaOrchestrator, SagaController |
| `domain/outbox` | OutboxEvent Entity/Repo, OutboxService, OutboxRelayScheduler |
| `domain/deadletter` | DeadLetterEvent Entity/Repo, DeadLetterService |
| `domain/reconciliation` | ReconciliationResult Entity/Repo, ReconciliationService |
| `global` | IdempotencyKey, TransactionAuditLog Entity/Repo, IdempotencyService, AuditService, KafkaConfig |

**StockCoreClient 확장:**
- `depositCash(accountId, StockCashRequest)` 추가
- `withdrawCash(accountId, StockCashRequest)` 추가

**Saga API:**
```
POST /baas/v1/saga/bank-to-stock   Header: Idempotency-Key, X-Firebase-Uid
POST /baas/v1/saga/stock-to-bank   Header: Idempotency-Key, X-Firebase-Uid
GET  /baas/v1/saga/{sagaId}
GET  /baas/v1/saga/{sagaId}/steps
```

---

# 5. Saga 흐름

## 5-1. BANK_TO_STOCK 성공 흐름

```text
[Client] POST /baas/v1/saga/bank-to-stock
    ↓
[transaction-server]
  1. IdempotencyService.check()
  2. SagaTransaction 생성 (STARTED)
  3. OutboxEvent 저장 (saga.started)

  [STEP 1] POST /internal/v1/bank/transfers
    → bank-server: 이체 예약, REQUESTED 반환 (잔액 변동 없음)
    → SagaStepHistory(BANK_TRANSFER_RESERVE, SUCCESS)

  [STEP 2] POST /internal/v1/stock/accounts/{id}/cash/deposit
    → stock-server: 예수금 증가
    → SagaStepHistory(STOCK_CASH_DEPOSIT, SUCCESS)

  [STEP 3] POST /internal/v1/bank/transfers/{id}/approve
    → bank-server: 실제 출금 확정 (Saga Commit)
    → SagaStepHistory(BANK_TRANSFER_COMMIT, SUCCESS)
    → SagaTransaction(SUCCESS)
    → OutboxEvent 저장 (saga.completed)
    → AuditLog 기록
    → IdempotencyService.complete()
```

## 5-2. BANK_TO_STOCK 실패 흐름

```text
[Case A] STEP 2 실패 — stock deposit 실패, bank 아직 미커밋

  approve 미호출 → 잔액 변동 없음
  SagaStepHistory(STOCK_CASH_DEPOSIT, FAILED)
  SagaTransaction(FAILED)
  OutboxEvent 저장 (saga.failed)

  ※ REQUESTED 상태 이체는 Reconciliation 배치로 주기적 정리

---

[Case B] STEP 3 실패 — bank approve 실패, stock 이미 예수금 증가

  SagaStepHistory(BANK_TRANSFER_COMMIT, FAILED)
  SagaTransaction(COMPENSATING)
  OutboxEvent 저장 (saga.compensation.started)

  [COMPENSATION] POST /internal/v1/stock/accounts/{id}/cash/withdraw
    → stock-server: 예수금 회수
    → SagaStepHistory(STOCK_CASH_WITHDRAW_COMPENSATION, COMPENSATED)
    → SagaTransaction(COMPENSATED)
    → OutboxEvent 저장 (saga.compensation.completed)
```

## 5-3. STOCK_TO_BANK 흐름 (역방향)

```text
[STEP 1] POST /internal/v1/stock/accounts/{id}/cash/withdraw
  → stock-server: 예수금 차감

[STEP 2] POST /internal/v1/bank/transfers
  → bank-server: 이체 예약 (REQUESTED)

[STEP 3] POST /internal/v1/bank/transfers/{id}/approve
  → bank-server: 은행 입금 확정

실패 시 Compensation: stockCoreClient.depositCash() 호출
```

---

# 6. Kafka 토픽

각 서버는 자신의 처리 결과를 Outbox 패턴을 통해 Kafka에 발행한다.

## 6-1. bank-server publish

bank-server가 자체 Outbox → Relay Worker를 통해 발행한다.

| 토픽 | 설명 |
|------|------|
| `bank.account.withdraw.completed` | 은행 계좌 출금 성공. stock 예수금 충전 진행 가능 |
| `bank.account.withdraw.failed` | 은행 계좌 출금 실패. Saga 중단 및 롤백 필요 |
| `bank.account.deposit.completed` | 은행 계좌 입금 성공. 증권 예수금 환불 완료 처리 가능 |
| `bank.account.deposit.failed` | 은행 계좌 입금 실패. 보상 트랜잭션 실패 상태 |

## 6-2. stock-server publish

stock-server가 자체 Outbox → Relay Worker를 통해 발행한다.

| 토픽 | 설명 |
|------|------|
| `stock.cash.deposit.completed` | 증권 예수금 충전 성공. 은행 → 증권 자금 이동 완료 |
| `stock.cash.deposit.failed` | 증권 예수금 충전 실패. 은행 출금 롤백 필요 |
| `stock.cash.withdraw.completed` | 증권 예수금 차감 성공. 증권 → 은행 환불 진행 가능 |
| `stock.cash.withdraw.failed` | 증권 예수금 차감 실패. Saga 중단 및 롤백 필요 |

## 6-3. transaction-server publish

transaction-server가 OutboxRelayScheduler를 통해 발행한다.

| 토픽 | 설명 |
|------|------|
| `saga.started` | 분산 트랜잭션 시작 |
| `saga.completed` | 모든 단계 성공. 최종 정합성 완료 |
| `saga.failed` | 트랜잭션 실패 발생. 보상 트랜잭션 필요 상태 |
| `saga.compensation.started` | 롤백(보상 트랜잭션) 시작 |
| `saga.compensation.completed` | 롤백 완료. 원장 정합성 복구 완료 |

## 6-4. Outbox 책임

| 서버 | Outbox 구현 책임 |
|------|-----------------|
| bank-server | 자체 Outbox 테이블 + Relay Worker 구현 |
| stock-server | 자체 Outbox 테이블 + Relay Worker 구현 |
| transaction-server | `OUTBOX_EVENT` 테이블 + `OutboxRelayScheduler` 구현 |

**OutboxRelayScheduler (transaction-server):**  
`@Scheduled(fixedDelay=1000)` → `published_yn=false` 이벤트 최대 100건 Kafka 발행  
3회 실패 시 → `DeadLetterEvent` 저장

---

# 7. 내부 API 공통 헤더

```http
X-User-Id: {xUserId}
X-Trace-Id: {uuid}
```

JWT 인증 없음. transaction-server가 인증 완료 후 내부 호출.
