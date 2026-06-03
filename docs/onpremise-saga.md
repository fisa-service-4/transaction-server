# 온프레미스 Saga 설계 문서

> 관련 서버: `transaction-server` · `bank-server` · `stock-server`
> 최종 수정: 2026-06-03
> 공유 대상: 온프레미스 3개 서버 전체

---

# 1. 전체 구조

```
service-backend
    ↓  REST (기존 transfer API 그대로)
transaction-server  ← Saga Orchestrator + 채널계 Orchestration Layer (8083, /baas/v1)
    ↓  OpenFeign                ↓  OpenFeign
bank-server (8081)        stock-server (8082)
/internal/v1/bank         /internal/v1/stock
```

**원칙:**
- service-backend는 transaction-server만 호출한다. 기존 transfer API를 그대로 사용한다.
- Saga 유형 판단(bank-to-stock / stock-to-bank / 일반 이체)은 transaction-server 내부에서 수행한다.
- bank-server / stock-server는 transaction-server 내부에서만 호출한다. 외부 직접 접근 금지.

---

# 2. 아키텍처 레이어 정의

## Control Plane — REST Orchestration

Saga 실행의 제어 흐름. transaction-server가 bank/stock을 동기 REST로 순차 호출하며 성공/실패를 즉시 판단한다. Kafka는 제어 흐름에 관여하지 않는다.

```
transaction-server
  → bank-server: POST /transfers          (결과 즉시 수신)
  → stock-server: POST /cash/deposit      (결과 즉시 수신)
  → bank-server: POST /transfers/{id}/approve  (결과 즉시 수신)
```

## Event Plane — Outbox → Kafka

transaction-server는 온프레미스 금융 거래의 **source-of-truth event stream 제공자**다. Saga 상태 변화를 Outbox 패턴으로 Kafka에 발행한다. 이 이벤트 스트림은 감사, 알림, 분석 시스템의 단일 진실 공급원으로 사용된다.

```
Saga 상태 변경
  + OUTBOX_EVENT INSERT (동일 트랜잭션)
  → OutboxRelayScheduler → Kafka
  → 감사 / 알림 / 분석 시스템 (downstream)
```

transaction-server는 채널계(channel layer) 스타일의 orchestration layer다. 서비스 요청을 수신하고, 내부 코어 서버(bank / stock)를 조율하며, 그 결과를 이벤트 스트림으로 기록한다.

---

# 3. 핵심 설계 결정 및 이유

## 3-1. Saga 진입점 — 기존 transfer API 재사용

**결정:** service-backend는 기존 `POST /baas/v1/bank/transfers`를 그대로 호출한다. saga-specific API(`/saga/bank-to-stock` 등)를 별도로 노출하지 않는다.

**이유:**
- service-backend 입장에서 "이체"라는 도메인 행위는 동일하다. 목적지가 증권계좌인지 은행계좌인지는 내부 구현 세부사항이다.
- workflow-specific API를 외부에 노출하면 클라이언트가 Saga 내부 구조에 종속된다.
- transaction-server가 `toBankCode` / `toAccountNumber`로 이체 유형을 판단해 내부 라우팅한다.

**라우팅 기준:**

| 조건 | 내부 처리 |
|------|----------|
| `fromAccountId` = 은행계좌, `toBankCode` = 증권사 코드 | BANK_TO_STOCK Saga |
| `fromAccountId` = 증권계좌 | STOCK_TO_BANK Saga |
| 그 외 | 기존 bank-to-bank 이체 |

## 3-2. Saga 구동을 REST로 한 이유 (Kafka Choreography 미선택)

| 비교 항목 | REST Orchestration | Kafka Choreography |
|-----------|-------------------|--------------------|
| 실패 감지 | 즉시 (HTTP 응답) | Consumer가 응답 이벤트 대기 |
| 보상 시점 | 실패 직후 즉시 | 이벤트 도달 후 |
| 디버깅 | 단일 traceId로 추적 | 여러 서비스 로그 조합 필요 |
| 타임아웃 처리 | OpenFeign 레벨 처리 | Correlation ID + 타임아웃 리스너 필요 |
| 네트워크 | AWS↔OnPrem VPN 직접 | Kafka broker 경유 추가 hop |

Control plane은 REST. Event plane은 Kafka. 둘을 섞지 않는다.

## 3-3. Kafka의 역할 정의

> **Kafka는 현재 Saga 제어에 사용되지 않는다.
> 현재 단계에서는 Audit/Event Streaming 목적으로만 사용하며,
> 향후 Notification/Analytics 서비스 연계 시 활용한다.**

transaction-server는 온프레미스 금융 거래의 source-of-truth event stream 제공자다. Saga 생명주기 이벤트를 Outbox를 통해 신뢰성 있게 발행한다.

발행 토픽 (4종):
```
saga.started
saga.completed
saga.failed
saga.compensated
```

bank/stock은 REST Core Service로 남는다. Kafka 발행 없음. transaction-server만 발행한다.

## 3-4. bank/stock이 Kafka를 직접 발행하지 않는 이유

**현재 결정: bank/stock Kafka 발행 없음.**

### 결정 이유

**① Consistency model 혼재 문제**

| 서버 | 발행 방식 | 신뢰도 |
|------|----------|--------|
| transaction-server | Outbox (DB 트랜잭션 보장) | 높음 |
| bank (가정) | 직접 발행 (best-effort) | 낮음 |
| stock (가정) | 직접 발행 (best-effort) | 낮음 |

같은 Saga 결과를 표현하는 이벤트들이 신뢰도가 다르면, downstream consumer가 어느 이벤트를 기준으로 삼아야 하는지 불명확해진다. `saga.completed`와 `stock.cash.deposit.completed`가 모두 발행된다면 둘 중 어느 게 권위 있는 이벤트인가?

**② 현재 Consumer 없음**

현재 아무 시스템도 bank/stock의 개별 이벤트를 소비하지 않는다. 발행 비용만 발생하고 이점이 없다.

**③ 이벤트 중복**

`saga.completed` payload에 어떤 단계가 완료됐는지(transferId, accountId, amount 등) 포함하면 bank/stock 개별 이벤트는 정보 중복이다.

**④ 장애 포인트 증가**

bank/stock에서 직접 발행하면 "비즈니스 처리 성공 + Kafka 발행 실패"가 독립적으로 발생할 수 있다. Outbox 없이는 이 불일치를 복구할 방법이 없다.

---

### bank/stock이 Kafka를 발행하려면 추가로 필요한 것

만약 향후 bank/stock이 직접 Kafka를 발행해야 하는 요구사항이 생긴다면, 아래가 추가로 필요하다. **직접 발행(best-effort)과 Outbox 보장 방식으로 나뉜다.**

**방식 A — 직접 발행 (best-effort, 권장하지 않음)**

각 서버에 추가 필요:
- Kafka producer 설정 (`spring-kafka`, bootstrap-servers 등)
- 비즈니스 로직 내 `kafkaTemplate.send()` 호출
- 발행 실패 시 복구 방법 없음 (이벤트 유실 허용 결정 필요)

문제: 비즈니스 DB 커밋 성공 후 Kafka 발행 실패 시 이벤트 유실. consistency model이 깨짐.

**방식 B — Outbox 패턴 (신뢰성 보장, 구현 비용 높음)**

각 서버에 추가 필요:
- `OUTBOX_EVENT` 테이블 (bank DB, stock DB 각각)
- 비즈니스 처리 + OUTBOX_EVENT INSERT를 동일 트랜잭션으로 묶는 로직
- `OutboxRelayScheduler` (@Scheduled, 각 서버별 독립 구현)
- Kafka 발행 실패 시 retry 로직 (retry_count 관리)
- `DEAD_LETTER_EVENT` 테이블 + 처리 로직 (3회 실패 이벤트)
- Scheduler / DLQ 모니터링 운영 체계

즉, transaction-server에 구현된 Outbox 인프라를 bank/stock에도 각각 복제해야 한다. 구현 비용과 운영 복잡도가 3배가 된다.

## 3-5. Outbox를 transaction-server에만 적용한 이유

Outbox를 전 서버에 강제하면 bank/stock/transaction 모두 OUTBOX_EVENT 테이블 + RelayScheduler + DLQ 관리가 필요하다. 현재 Kafka Consumer가 없으므로 bank/stock 이벤트 신뢰도가 낮아도 서비스에 영향이 없다. transaction-server의 Saga 이벤트는 source-of-truth이므로 Outbox로 보장한다.

| 모델                    | 추천도   | 현실성   | MSA purity | 구현난이도 |
| --------------------- | ----- | ----- | ---------- | ----- |
| **1. 현재 모델**          | ⭐⭐⭐⭐⭐ | 매우 높음 | 중간         | 낮음    |
| **2. 각 서버가 direct publish** | ⭐     | 낮음    | 애매         | 중간    |
| **3. 각 서버 all outbox**     | ⭐⭐⭐   | 높음    | 높음         | 매우 높음 |


## 3-6. Idempotency 패턴을 bank/stock 동일하게 구성한 이유

Feign 타임아웃 후 재시도 시 stock에서 double deposit이 발생할 수 있다. bank는 이미 `Idempotency-Key` 헤더로 중복을 처리한다. stock도 동일한 헤더 기반 패턴을 적용해 인터페이스를 통일한다. transaction-server가 operation별 키를 생성해서 헤더로 전달하므로 stock-server는 Saga 개념을 알 필요 없다.

---

# 4. bank 담당자에게 전달할 내용

## 4-1. 기존 API 활용 (변경 없음)

현재 Saga 설계에서 bank-server의 기존 API를 그대로 활용한다.

| 단계 | 메서드 | 경로 | 동작 |
|------|--------|------|------|
| Transfer 생성 | POST | `/internal/v1/bank/transfers` | 이체 레코드 생성 → `REQUESTED` 반환 **(잔액 변동 없음)** |
| Transfer 확정 | POST | `/internal/v1/bank/transfers/{id}/approve` | 잔액 검증 + 실제 출금/입금 실행 → `SUCCESS` 반환 |
| Transfer 조회 | GET | `/internal/v1/bank/transfers/{id}` | 상태 조회 (approve timeout 후 상태 확인에 사용) |

**Idempotency 현황 (이미 충족):**
- `createTransfer`: `Idempotency-Key` 헤더로 중복 생성 방지 ✅
- `approveTransfer`: 동일 transferId 재호출 시 `TRANSFER_003` 반환 (자연 멱등) ✅

## 4-2. "Reserve"가 아닌 이유 (명칭 수정)

`createTransfer`를 "Reserve"로 표현했으나 실제 동작은 잔액을 잠그지 않는다. 이체 레코드만 INSERT하고 잔액은 `approve` 시점에 처음 차감된다.

실질적 의미: **pending intent recording** (이체 의도 기록)

Saga step명을 `BANK_TRANSFER_REQUEST_CREATED`로 사용한다. bank-server 구현에는 영향 없음.

## 4-3. cancel API 추가 — 구현 확정

**결정: cancel API 구현한다.**

STEP 2(stock deposit) 실패 시 approve를 호출하지 않아 잔액 변동은 없지만, `REQUESTED` 상태 이체 레코드가 bank DB에 남는다. cancel API로 즉시 명시적으로 종료한다.

```http
POST /internal/v1/bank/transfers/{id}/cancel
→ REQUESTED → CANCELLED
```

구현: `UPDATE transfer SET status = 'CANCELLED' WHERE id = ? AND status = 'REQUESTED'`

**결정 이유:**
- Reconciliation 배치 의존 없이 Case A가 즉시 종료됨
- transfer 테이블에 의미 없는 REQUESTED 레코드 누적 방지
- 구현 난이도 매우 낮음 (단순 UPDATE 1건)

---

# 5. 서버별 구현 내용

## 5-1. bank-server

**신규 구현 없음.** 기존 API 사용. Kafka 발행 없음.

## 5-2. stock-server

**신규 구현 대상:**

| 메서드 | 경로 | 동작 |
|--------|------|------|
| POST | `/internal/v1/stock/accounts/{accountId}/cash/deposit` | 예수금 증가 |
| POST | `/internal/v1/stock/accounts/{accountId}/cash/withdraw` | 예수금 차감 |

**공통 헤더:**
```
X-User-Id: {userId}
X-Trace-Id: {traceId}
Idempotency-Key: {sagaId}_{OPERATION_TYPE}    ← transaction-server가 생성해서 전달
```

**Request body:**
```json
{ "amount": 500000 }
```

**Response body:**
```json
{ "accountId": 2001, "cashBalance": 10500000 }
```

**에러:** withdraw 잔액 부족 → `TRANSFER_002`

**Idempotency 구현 (bank와 동일 패턴):**
- `Idempotency-Key` 헤더 수신
- 동일 키가 이미 처리됐으면 저장된 응답 반환
- 신규 요청이면 처리 후 결과 저장

Kafka 발행 없음.

**구현 필요 사항:**
- `CashService`: `deposit(accountId, amount, idempotencyKey)` / `withdraw(accountId, amount, idempotencyKey)`
- `CashController`: 위 2개 엔드포인트
- Idempotency-Key 기반 중복 처리 로직

## 5-3. transaction-server

**전체 신규 구현 대상.**

### 도메인 패키지

| 패키지 | 구현 내용 |
|--------|-----------|
| `domain/saga` | SagaTransaction, SagaStepHistory Entity/Repo, SagaOrchestrator |
| `domain/outbox` | OutboxEvent Entity/Repo, OutboxService, OutboxRelayScheduler |
| `domain/deadletter` | DeadLetterEvent Entity/Repo, DeadLetterService |
| `domain/reconciliation` | ReconciliationResult Entity/Repo, ReconciliationService |
| `global` | IdempotencyKey Entity/Repo, TransactionAuditLog Entity/Repo, IdempotencyService, AuditService, KafkaConfig |

### StockCoreClient 추가 메서드

```java
@PostMapping("/internal/v1/stock/accounts/{accountId}/cash/deposit")
ApiResponse<StockCashResponse> depositCash(
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-Trace-Id") String traceId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @PathVariable("accountId") Long accountId,
    @RequestBody StockCashRequest request);

@PostMapping("/internal/v1/stock/accounts/{accountId}/cash/withdraw")
ApiResponse<StockCashResponse> withdrawCash(
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-Trace-Id") String traceId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @PathVariable("accountId") Long accountId,
    @RequestBody StockCashRequest request);
```

`StockCashRequest`: `{ Long amount }`
`StockCashResponse`: `{ Long accountId, BigDecimal cashBalance }`

**Idempotency-Key 생성 규칙 (transaction-server 내부):**

| 호출 대상 | 생성 키 |
|-----------|---------|
| 정상 deposit | `{sagaId}_DEPOSIT` |
| 정상 withdraw | `{sagaId}_WITHDRAW` |
| compensation deposit | `{sagaId}_COMPENSATION_DEPOSIT` |
| compensation withdraw | `{sagaId}_COMPENSATION_WITHDRAW` |

### 내부 라우팅 로직

```
POST /baas/v1/bank/transfers 수신
    ↓
fromAccountId 계좌 유형 조회
    ↓
├── 은행계좌 → 목적지 분석
│       ├── toBankCode = 증권사 코드 → BANK_TO_STOCK Saga
│       └── 그 외                  → 기존 bank-to-bank 이체
└── 증권계좌 → STOCK_TO_BANK Saga
```

### OutboxRelayScheduler

```java
@Scheduled(fixedDelay = 1000)
void relay() {
    // published_yn = false 이벤트 최대 100건 조회 → Kafka 발행
    // 실패 시 retry_count++
    // retry_count >= 3 → DeadLetterEvent 저장
}
```

### DB 테이블 목록

| 테이블 | 설명 |
|--------|------|
| `SAGA_TRANSACTION` | Saga 트랜잭션 상태 |
| `SAGA_STEP_HISTORY` | 단계별 처리 이력 |
| `OUTBOX_EVENT` | Kafka 발행 보장 |
| `DEAD_LETTER_EVENT` | 3회 실패 이벤트 |
| `IDEMPOTENCY_KEY` | 중복 Saga 요청 방지 |
| `TRANSACTION_AUDIT_LOG` | 거래 감사 로그 |
| `RECONCILIATION_RESULT` | 정합성 검증 결과 |

---

# 6. Saga 흐름

## 6-1. BANK_TO_STOCK 성공 흐름

```
[service-backend] POST /baas/v1/bank/transfers
Header: Idempotency-Key: {uuid}, X-User-Id: {userId}, X-Trace-Id: {traceId}
Body: { fromAccountId: 1001, toBankCode: "039", toAccountNumber: "...", transferAmount: 500000 }
    ↓
[transaction-server] 라우팅: toBankCode = 증권사 → BANK_TO_STOCK Saga

  1. IdempotencyService.check(idempotencyKey)
     → 이미 존재하면 저장된 응답 반환 (종료)
  2. SagaTransaction INSERT (STARTED)
     + OutboxEvent INSERT (saga.started)
     → 동일 트랜잭션 Commit

  ─── STEP 1: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
  3. BankCoreClient.createTransfer(fromAccountId, toBankCode, toAccountNumber, amount)
     Header: Idempotency-Key = {idempotencyKey}
     → bank: transfer INSERT, 잔액 변동 없음 → REQUESTED 반환, transferId 수신
     → SagaStepHistory INSERT (BANK_TRANSFER_REQUEST_CREATED, SUCCESS)

  ─── STEP 2: STOCK_CASH_DEPOSIT ──────────────────────────────────
  4. StockCoreClient.depositCash(toStockAccountId, amount)
     Header: Idempotency-Key = "{sagaId}_DEPOSIT"
     → stock: cash_balance 증가
     → SagaStepHistory INSERT (STOCK_CASH_DEPOSIT, SUCCESS)

  ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
  5. BankCoreClient.approveTransfer(transferId)
     → bank: 잔액 검증 + 실제 출금 실행 → SUCCESS
     → SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, SUCCESS)
     → SagaTransaction UPDATE (SUCCESS)
     + OutboxEvent INSERT (saga.completed)
     → 동일 트랜잭션 Commit
     → TransactionAuditLog INSERT
     → IdempotencyService.complete(idempotencyKey, response)
```

## 6-2. BANK_TO_STOCK 실패 흐름

### Case A — STEP 2 실패 (stock deposit 오류)

```
StockCoreClient.depositCash() 실패
    ↓
STEP 3(approve) 미호출 → bank 잔액 변동 없음
SagaStepHistory INSERT (STOCK_CASH_DEPOSIT, FAILED)
SagaTransaction UPDATE (FAILED)
+ OutboxEvent INSERT (saga.failed)
→ 동일 트랜잭션 Commit

[cancel API 있는 경우]
  BankCoreClient.cancelTransfer(transferId) → REQUESTED → CANCELLED

[cancel API 없는 경우]
  REQUESTED 이체 방치 → Reconciliation 배치 주기적 정리
```

### Case B — STEP 3 실패 (bank approve 오류, stock 예수금 이미 증가)

```
BankCoreClient.approveTransfer() → 명확한 실패 응답 수신
    ↓
SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, FAILED)
SagaTransaction UPDATE (COMPENSATING)
+ OutboxEvent INSERT (saga.compensated (보상 시작))
→ 동일 트랜잭션 Commit

  ─── COMPENSATION: STOCK_CASH_WITHDRAW ───────────────────────────
  StockCoreClient.withdrawCash(toStockAccountId, amount)
  Header: Idempotency-Key = "{sagaId}_COMPENSATION_WITHDRAW"
  → stock: 예수금 차감
  → SagaStepHistory INSERT (STOCK_CASH_WITHDRAW_COMPENSATION, COMPENSATED)
  → SagaTransaction UPDATE (COMPENSATED)
  + OutboxEvent INSERT (saga.compensated)
  → 동일 트랜잭션 Commit
```

### Case UNKNOWN — STEP 3 timeout (approve 결과 불명)

분산 환경에서 네트워크 timeout 시 bank가 실제로 처리했는지 알 수 없다. 가장 위험한 케이스.

```
BankCoreClient.approveTransfer() → Feign timeout (응답 없음)
    ↓
즉시 실패로 처리하지 않고 상태 조회 시도

  1. BankCoreClient.getTransfer(transferId) 조회
     ├── SUCCESS   → bank 처리 완료 → Saga SUCCESS로 처리
     ├── REQUESTED → bank 미처리 → Case B(Compensation) 흐름으로 처리
     └── 조회도 실패 / 응답 없음
         → SagaTransaction UPDATE (UNKNOWN)
         + OutboxEvent INSERT (saga.unknown)
         → 수동 개입 필요 (운영팀 알림)

UNKNOWN 상태: 자동 처리 불가. 운영팀이 bank/stock 원장 직접 확인 후 수동 완료 또는 보상 처리.
```

## 6-3. STOCK_TO_BANK 성공 흐름

```
[service-backend] POST /baas/v1/bank/transfers
Body: { fromAccountId: 2001 (증권계좌), toBankCode: "088", toAccountNumber: "...", amount: 500000 }
    ↓
[transaction-server] 라우팅: fromAccountId = 증권계좌 → STOCK_TO_BANK Saga

  1. IdempotencyService.check()
  2. SagaTransaction INSERT (STARTED) + OutboxEvent (saga.started)

  ─── STEP 1: STOCK_CASH_WITHDRAW ─────────────────────────────────
  3. StockCoreClient.withdrawCash(fromStockAccountId, amount)
     Header: Idempotency-Key = "{sagaId}_WITHDRAW"
     → stock: 예수금 차감
     → SagaStepHistory INSERT (STOCK_CASH_WITHDRAW, SUCCESS)

  ─── STEP 2: BANK_TRANSFER_REQUEST_CREATED ───────────────────────
  4. BankCoreClient.createTransfer(계좌 정보, amount)
     Header: Idempotency-Key = {idempotencyKey}
     → bank: transfer REQUESTED
     → SagaStepHistory INSERT (BANK_TRANSFER_REQUEST_CREATED, SUCCESS)

  ─── STEP 3: BANK_TRANSFER_COMMIT ────────────────────────────────
  5. BankCoreClient.approveTransfer(transferId)
     → bank: 입금 실행
     → SagaStepHistory INSERT (BANK_TRANSFER_COMMIT, SUCCESS)
     → SagaTransaction UPDATE (SUCCESS) + OutboxEvent (saga.completed)
     → AuditLog + IdempotencyKey 완료
```

## 6-4. STOCK_TO_BANK 실패 흐름

### STEP 1 실패 (stock withdraw 오류)

```
StockCoreClient.withdrawCash() 실패 (TRANSFER_002 잔액 부족 등)
    ↓
SagaStepHistory INSERT (STOCK_CASH_WITHDRAW, FAILED)
SagaTransaction UPDATE (FAILED) + OutboxEvent (saga.failed)
→ 보상 없음 (stock 변동 없음, bank 미호출)
```

### STEP 2/3 실패 또는 STEP 3 UNKNOWN (stock 예수금 이미 차감)

```
STEP 2 또는 STEP 3 실패 / timeout
    ↓
[실패] SagaTransaction UPDATE (COMPENSATING)
[timeout] 상태 조회 후 미처리 확인 → COMPENSATING

+ OutboxEvent INSERT (saga.compensated (보상 시작))

  ─── COMPENSATION: STOCK_CASH_DEPOSIT ────────────────────────────
  StockCoreClient.depositCash(fromStockAccountId, amount)
  Header: Idempotency-Key = "{sagaId}_COMPENSATION_DEPOSIT"
  → stock: 예수금 복구
  → SagaTransaction UPDATE (COMPENSATED) + OutboxEvent (saga.compensated)
```

---

# 7. Kafka 이벤트

## 7-1. transaction-server 발행 (Outbox 경유 — 보장됨)

transaction-server가 온프레미스 금융 거래의 source-of-truth event stream을 제공한다.
현재 단계에서는 Audit/Event Streaming 목적. 향후 Notification/Analytics 서비스 연계 시 활용.

| 토픽 | 발행 시점 | 주요 payload |
|------|-----------|-------------|
| `saga.started` | Saga 시작 | sagaId, sagaType, amount |
| `saga.completed` | 모든 단계 성공 | sagaId, steps 결과 |
| `saga.failed` | 단계 실패, 보상 불필요 | sagaId, failedStep, reason |
| `saga.compensated` | 보상 트랜잭션 완료 | sagaId, compensatedStep |

> `saga.unknown` (approve timeout, 상태 불명) 은 운영 알림용으로 별도 처리. 필요 시 추가.

## 7-2. bank-server / stock-server

**Kafka 발행 없음.** REST 응답만 반환.

발행하지 않는 이유 및 발행 시 추가 필요사항 → 섹션 3-4 참조.

---

# 8. 외부 API (service-backend 호출 대상)

기존 transfer API를 그대로 사용한다. saga-specific 엔드포인트 없음.

| 메서드 | 경로 | 용도 |
|--------|------|------|
| POST | `/baas/v1/bank/transfers` | 이체 요청 (내부 라우팅으로 Saga 또는 일반 이체) |
| GET | `/baas/v1/bank/transfers/{transferId}` | 이체 결과 조회 |

**요청 헤더:**
```
Idempotency-Key: {uuid}
X-User-Id: {userId}
X-Trace-Id: {traceId}
```

# 9. 내부 API 목록

## 9-1. bank-server (기존 구현 완료)

| 메서드 | 경로 | 구현 상태 |
|--------|------|-----------|
| POST | `/internal/v1/bank/transfers` | ✅ 완료 |
| POST | `/internal/v1/bank/transfers/{id}/approve` | ✅ 완료 |
| GET | `/internal/v1/bank/transfers/{id}` | ✅ 완료 |
| POST | `/internal/v1/bank/transfers/{id}/cancel` | ❌ 미구현 (구현 확정) |

## 9-2. stock-server (신규 구현)

| 메서드 | 경로 | 구현 상태 |
|--------|------|-----------|
| POST | `/internal/v1/stock/accounts/{accountId}/cash/deposit` | ❌ 미구현 |
| POST | `/internal/v1/stock/accounts/{accountId}/cash/withdraw` | ❌ 미구현 |

---

# 10. 구현 순서

```
[1] transaction-server — DB 엔티티 / 레포지토리
    └─ SagaTransaction, SagaStepHistory
    └─ OutboxEvent, DeadLetterEvent
    └─ IdempotencyKey, TransactionAuditLog, ReconciliationResult

[2] stock-server — Cash API
    └─ CashService (deposit / withdraw + idempotency)
    └─ CashController

[3] transaction-server — StockCoreClient 확장
    └─ depositCash() / withdrawCash() (Idempotency-Key 헤더 포함)

[4] transaction-server — 내부 라우팅 + BANK_TO_STOCK 성공 흐름
    └─ SagaOrchestrator.route() (계좌 유형 판단)
    └─ SagaOrchestrator.bankToStock()

[5] transaction-server — Idempotency + Audit
    └─ IdempotencyService
    └─ AuditService

[6] transaction-server — Outbox
    └─ OutboxService
    └─ OutboxRelayScheduler
    └─ KafkaConfig

[7] transaction-server — 실패 / Compensation / UNKNOWN 흐름
    └─ Case A (STEP 2 실패)
    └─ Case B (STEP 3 실패 + Compensation)
    └─ UNKNOWN (timeout → 상태 조회 → 분기)

[8] transaction-server — STOCK_TO_BANK 흐름
```

---

# 11. SagaStepName 열거형

| 값 | 방향 | 설명 |
|----|------|------|
| `BANK_TRANSFER_REQUEST_CREATED` | 공통 | bank 이체 레코드 생성 (잔액 변동 없음) |
| `STOCK_CASH_DEPOSIT` | BANK_TO_STOCK 정상 | stock 예수금 충전 |
| `BANK_TRANSFER_COMMIT` | 공통 | bank 이체 확정 (실제 출금/입금) |
| `STOCK_CASH_WITHDRAW` | STOCK_TO_BANK 정상 | stock 예수금 차감 |
| `STOCK_CASH_WITHDRAW_COMPENSATION` | BANK_TO_STOCK 보상 | stock 예수금 회수 |
| `STOCK_CASH_DEPOSIT_COMPENSATION` | STOCK_TO_BANK 보상 | stock 예수금 복구 |

---

# 12. SagaTransaction 상태 전이

```
STARTED
  → (STEP 진행) PROCESSING
  → (모든 STEP 성공) SUCCESS

PROCESSING
  → (단계 실패, 보상 불필요) FAILED
  → (단계 실패, 보상 필요) COMPENSATING
  → (approve timeout, 상태 조회 실패) UNKNOWN

COMPENSATING
  → (보상 성공) COMPENSATED
  → (보상 실패) COMPENSATION_FAILED

UNKNOWN           ← 수동 개입 필요
COMPENSATION_FAILED ← 수동 개입 필요
```

---

# 13. 고려사항 및 제약

| 항목 | 내용 |
|------|------|
| approve timeout | bank GET으로 상태 확인 후 분기. 확인 불가 시 UNKNOWN |
| UNKNOWN 처리 | 자동 처리 불가. 운영팀 수동 확인 필요. saga.unknown Kafka 이벤트로 알림 |
| COMPENSATION_FAILED | 보상도 실패한 경우. 원장 불일치 상태. 수동 처리 필요 |
| Case A cancel API 미보유 | REQUESTED 이체는 Reconciliation 배치로 주기 정리 |
| stock cash idempotency | Idempotency-Key 헤더 기반. 동일 키 재요청 시 캐시 응답 반환 |
| bank reserve 의미 | 잔액 잠금 없음. 이체 의도 기록만 함. approve 시점에 잔액 검증 |
