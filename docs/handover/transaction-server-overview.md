# transaction-server 소개

> 작성일: 2026-06-15
> 대상: 이 서버를 처음 접하는 팀원
> 심화 문서 목록 → [8. 참조 문서](#8-참조-문서)

---

## 1. 서버 역할

transaction-server는 온프레미스 금융 시스템의 **Saga Orchestrator이자 채널계 오케스트레이션 레이어**다.

```
service-backend
    ↓  REST (POST /baas/v1/bank/transfers)
transaction-server  ← 이 서버 (Port 8083)
    ↓  OpenFeign              ↓  OpenFeign
bank-server (8081)      stock-server (8082)
```

service-backend는 transaction-server만 호출한다. bank-server / stock-server는 transaction-server 내부에서만 호출되며 외부 직접 접근이 금지된다.

### 핵심 책임 3가지

| 책임 | 설명 |
|------|------|
| **Saga Orchestration** | 은행↔증권 간 분산 트랜잭션을 REST 동기 호출로 순서 제어, 실패 시 보상 트랜잭션 실행 |
| **Transactional Outbox** | Saga 상태 변화를 Kafka에 신뢰성 있게 발행 (감사 / 알림 / 분석 downstream용) |
| **DLQ · Reconciliation** | Kafka 발행 실패 이벤트 보관 및 재처리, Saga 이상 상태 주기 감지 |

### 왜 별도 서버인가

분산 트랜잭션 제어 로직을 service-backend에 두면 비즈니스 레이어가 금융 원장 내부 구조에 종속된다. transaction-server가 분산 트랜잭션의 진입점이자 Kafka event source-of-truth를 단일하게 관리한다.

---

## 2. 기술 스택

| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 17 | 백엔드 언어 |
| Spring Boot | 3.x | 애플리케이션 서버 |
| Spring Data JPA | - | ORM |
| Oracle DB | - | 거래 DB (온프레미스) |
| Redis | 7.2 | Idempotency Key 캐시 |
| Kafka | 3.8 | 이벤트 스트리밍 (Outbox 경유) |
| OpenFeign | - | bank-server / stock-server 내부 호출 |

> **Oracle DB 사용 이유**: 이 서버는 온프레미스 폐쇄망에서 운영된다. 전체 프로젝트의 운영 DB는 PostgreSQL(AWS RDS)이지만, 온프레미스 금융 원장 서버(bank / stock / transaction)는 Oracle을 사용한다.

---

## 3. 패키지 구조

```
com.transaction/
├── domain/
│   ├── saga/           Saga 트랜잭션 상태 관리 및 오케스트레이션
│   ├── outbox/         Transactional Outbox 이벤트 저장 및 Kafka 발행
│   ├── deadletter/     Kafka 발행 3회 실패 이벤트 보관 및 재처리
│   ├── reconciliation/ Saga 이상 상태 주기 감지
│   ├── bank/           BankCoreClient (Feign), 이체 서비스
│   └── stock/          StockCoreClient (Feign), 예수금 서비스
│
└── global/
    ├── service/
    │   ├── IdempotencyService   중복 요청 방지 키 상태 머신
    │   └── AuditService         금융 거래 감사 로그
    └── config/
        ├── KafkaConfig          KafkaTemplate Producer 설정
        └── KafkaTopics          토픽 상수 (saga.started 등 4종)
```

---

## 4. 이체 요청 처리 흐름

service-backend가 `POST /baas/v1/bank/transfers`를 호출하면 transaction-server가 내부적으로 이체 유형을 판단해 라우팅한다.

### 4-1. 라우팅 규칙

```
fromAccountId 계좌 유형 조회
    ├── 은행 계좌 → toBankCode 분석
    │       ├── 증권사 코드 (243 / 247) → BANK_TO_STOCK Saga
    │       └── 그 외                  → 기존 bank-to-bank 이체
    └── 증권 계좌 → STOCK_TO_BANK Saga
```

### 4-2. BANK_TO_STOCK (은행 → 증권 예수금 충전)

```
STEP 1  BankCoreClient.createTransfer()      이체 레코드 생성 (잔액 변동 없음)
STEP 2  StockCoreClient.depositCash()        stock 예수금 충전
STEP 3  BankCoreClient.approveTransfer()     bank 실제 출금 확정
```

STEP 2가 먼저 실행되는 이유: approve 실패 시 stock을 되돌리는 게 bank를 되돌리는 것보다 단순하기 때문이다.

### 4-3. STOCK_TO_BANK (증권 예수금 → 은행 입금)

```
STEP 1  StockCoreClient.withdrawCash()       stock 예수금 차감
STEP 2  BankCoreClient.createTransfer()      이체 레코드 생성 (정산 계좌 사용)
STEP 3  BankCoreClient.approveTransfer()     bank 실제 입금 확정
```

> STOCK_TO_BANK는 증권사별 정산 계좌(`settlement account`)를 `fromAccountId`로 사용한다.
> 브로커 코드 → 정산 계좌 매핑: `broker.codes` 설정값 참조.

### 4-4. 실패 처리 분기

| Case | 트리거 | 처리 |
|------|--------|------|
| **Case A** | STEP 2 실패 | STEP 3 미호출 → FAILED (잔액 변동 없음) |
| **Case B** | STEP 3 실패 (명확한 오류 응답) | 보상 트랜잭션 실행 후 COMPENSATED |
| **UNKNOWN** | STEP 3 타임아웃 (결과 불명) | `getTransfer()` 상태 재조회 → SUCCESS 확정 또는 보상 또는 UNKNOWN |

UNKNOWN 상태는 자동 처리 불가. `saga.unknown` Kafka 이벤트 발행 후 운영팀 수동 개입.

---

## 5. Transactional Outbox 패턴

### 왜 Outbox를 쓰는가

Kafka에 직접 발행(best-effort)하면 "비즈니스 DB 커밋 성공 + Kafka 발행 실패" 불일치가 발생한다. Outbox 패턴은 비즈니스 상태 변경과 이벤트 저장을 같은 DB 트랜잭션으로 묶어 발행을 보장한다.

```
Saga 상태 변경
  + OUTBOX_EVENT INSERT   ← 동일 트랜잭션
        ↓
OutboxRelayScheduler (@Scheduled fixedDelay=5s)
        ↓
    Kafka 발행 성공 → markPublished()
    Kafka 발행 실패 → incrementRetry()
                       └── retryCount >= 3 → DeadLetterEvent 저장 + markPublished()
```

bank-server / stock-server는 Kafka를 발행하지 않는다. transaction-server가 온프레미스 금융 거래의 단일 이벤트 발행자다.

---

## 6. 핵심 보장 메커니즘

### 6-1. Idempotency Key

동일 요청이 중복으로 들어와도 한 번만 처리하기 위한 상태 머신.

```
[없음] → PROCESSING → SUCCESS  (complete 호출)
                    → FAILED   (fail 호출, 재시도 허용)
```

- PROCESSING 상태에서 동일 키 재요청 → `DuplicateRequestInProgressException`
- SUCCESS 상태 → 저장된 응답 즉시 반환 (Saga 미실행)
- FAILED 상태 → 재시도 허용 (PROCESSING으로 갱신)

### 6-2. Dead Letter Queue (DLQ)

Kafka 발행 3회 실패 이벤트를 `dead_letter_event` 테이블에 보관한다.
`resolved_yn = false` 이벤트는 운영 어드민에서 재처리 가능하다.

### 6-3. Reconciliation

`ReconciliationService`가 주기적으로 Saga 이상 상태를 집계해 `reconciliation_result`에 저장한다.

| 감지 대상 | 의미 |
|-----------|------|
| `COMPENSATION_FAILED` | 보상 트랜잭션도 실패 — 즉각 수동 개입 필요 |
| `UNKNOWN` | approve timeout 후 상태 불명 — 수동 확인 필요 |
| `PROCESSING` (stuck) | 설정 timeout 초과 후 처리 중 상태 지속 |
| `COMPENSATING` (stuck) | 설정 timeout 초과 후 보상 중 상태 지속 |

mismatchCount > 0이면 `status = FAILED`로 저장되어 운영팀에 알린다.

---

## 7. Kafka 토픽

transaction-server만 발행한다. 모두 Outbox 경유로 발행이 보장된다.

| 토픽 | 발행 시점 |
|------|-----------|
| `saga.started` | Saga 시작 시 |
| `saga.completed` | 모든 단계 성공 시 |
| `saga.failed` | 단계 실패, 보상 불필요 시 |
| `saga.compensated` | 보상 트랜잭션 완료 시 |

현재 단계에서는 감사(Audit) / Event Streaming 목적. 향후 Notification / Analytics 서비스 연계 시 Consumer 추가.

---

## 8. 참조 문서

| 문서 | 내용 |
|------|------|
| `docs/onpremise-saga.md` | Saga 설계 의도, 아키텍처 결정 근거, 3개 서버 전체 협의 내용 |
| `docs/transaction-server-saga.md` | 구현 계획, DB 스키마 DDL, 파일별 구현 목록 |
| `docs/api/api-transaction-server.md` | 외부 API 명세 (`/baas/v1`) |
| `docs/db/db-transaction-spec.md` | 거래 DB 테이블 명세 (Saga / Outbox / DLQ / Reconciliation) |
| `docs/transaction-test-spec.md` | 단위 테스트 명세 (36개, Mockito 기반) |
