# Saga 이체 API 테스트 문서

> **Transaction Server** `localhost:8083`
> 모든 이체 요청은 `POST /baas/v1/bank/transfers` 단일 엔드포인트로 진입합니다.
> transaction-server가 `fromAccountId` 계좌 유형과 `toAccountNumber`를 분석해 내부적으로 라우팅합니다.

---

## 라우팅 기준

| 조건 | 내부 처리 |
|---|---|
| `fromAccountId` = 증권 계좌 (`STOCK` 매핑) | **STOCK_TO_BANK** Saga |
| `fromAccountId` = 은행 계좌 + `toAccountNumber`가 본인 증권 계좌번호 일치 | **BANK_TO_STOCK** Saga |
| 그 외 | **기존 bank-to-bank** 이체 |

---

## 테스트 사전 조건

- git bash에 `winget install jqlang.jq` 설치
- bank-server (8081), stock-server (8082), transaction-server (8083) 모두 up
- oracle-onpremise 컨테이너 실행 중

---

## 공통 테스트 데이터

> 아래 데이터가 각 서버 DB에 수동으로 삽입된 상태를 가정합니다.

### 사용자 (transaction-server DB)

| 항목 | 값 |
|---|---|
| user_id | `777` |
| x_user_id | `777` |
| user_name | `IntegrationUser` |
| phone_number | `01077777777` |

### 은행 계좌 (bank-server DB)

| accountId | 계좌명 | 계좌번호 | 초기 잔액 |
|---|---|---|---|
| `1077` | INCOME통장 | `110-111-000074` | 5,000,000원 |
| `1078` | SALARY통장 | `110-111-000075` | 1,200,000원 |
| `1079` | EMERGENCY통장 | `110-111-000076` | 1,200,000원 |
| `1080` | INVEST통장 | `110-111-000077` | 1,200,000원 |

> 은행 코드: `777`

### 증권 계좌 (stock-server DB)

| 항목 | 값 |
|---|---|
| accountId | `25` |
| 계좌번호 | `300-777-000071` |
| broker_code | `039` |
| 초기 예수금 | 50,000,000원 |

### transaction-server DB 계좌 매핑

> 수동 삽입 SQL은 아래를 참고합니다.

```sql
-- user_master
MERGE INTO user_master t
USING (SELECT 777 user_id, 777 x_user_id, 'IntegrationUser' user_name, '01077777777' phone_number FROM dual) s
ON (t.user_id = s.user_id)
WHEN NOT MATCHED THEN INSERT (user_id, x_user_id, user_name, phone_number)
VALUES (s.user_id, s.x_user_id, s.user_name, s.phone_number);

-- 은행 계좌 매핑 (4개)
MERGE INTO user_account_mapping t
USING (SELECT 1077 account_id,'BANK' account_type,777 user_id,777 x_user_id FROM dual) s
ON (t.account_id=s.account_id AND t.account_type=s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id,account_type,user_id,x_user_id)
VALUES (s.account_id,s.account_type,s.user_id,s.x_user_id);

MERGE INTO user_account_mapping t
USING (SELECT 1078 account_id,'BANK' account_type,777 user_id,777 x_user_id FROM dual) s
ON (t.account_id=s.account_id AND t.account_type=s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id,account_type,user_id,x_user_id)
VALUES (s.account_id,s.account_type,s.user_id,s.x_user_id);

MERGE INTO user_account_mapping t
USING (SELECT 1079 account_id,'BANK' account_type,777 user_id,777 x_user_id FROM dual) s
ON (t.account_id=s.account_id AND t.account_type=s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id,account_type,user_id,x_user_id)
VALUES (s.account_id,s.account_type,s.user_id,s.x_user_id);

MERGE INTO user_account_mapping t
USING (SELECT 1080 account_id,'BANK' account_type,777 user_id,777 x_user_id FROM dual) s
ON (t.account_id=s.account_id AND t.account_type=s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id,account_type,user_id,x_user_id)
VALUES (s.account_id,s.account_type,s.user_id,s.x_user_id);

-- 증권 계좌 매핑
MERGE INTO user_account_mapping t
USING (SELECT 25 account_id,'STOCK' account_type,777 user_id,777 x_user_id FROM dual) s
ON (t.account_id=s.account_id AND t.account_type=s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id,account_type,user_id,x_user_id)
VALUES (s.account_id,s.account_type,s.user_id,s.x_user_id);

COMMIT;
```

---

## 공통 헤더 설명

| 헤더 | 설명 | 적용 대상 |
|---|---|---|
| `Idempotency-Key` | 중복 방지 키 (UUID 권장) | 이체 실행 필수 |
| `Content-Type` | `application/json` | 이체 실행 |

> `X-User-Id`, `X-Trace-Id` 는 transaction-server 내부에서 자동 처리됩니다. 외부에서 전달하지 않습니다.

---

# SAGA-1. BANK_TO_STOCK — 은행 → 증권 예수금 충전

> `fromAccountId`가 은행 계좌이고 `toAccountNumber`가 본인 증권 계좌번호와 일치하면 BANK_TO_STOCK Saga가 실행됩니다.
>
> **내부 흐름:**
> 1. STEP 1 — bank-server: 이체 레코드 생성 (잔액 변동 없음, REQUESTED)
> 2. STEP 2 — stock-server: 예수금 충전 (`cash_balance` 증가)
> 3. STEP 3 — bank-server: 이체 확정 (실제 출금 실행, SUCCESS)

---

## SAGA-BTS-001. 정상 흐름 — 은행 출금 + 증권 예수금 충전

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-bts-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1077,
    "toBankCode": "039",
    "toAccountNumber": "300-777-000071",
    "transferAmount": 500000,
    "requestedBy": "USER"
  }' | jq .
```

**기대 응답:**

```json
{
  "success": true,
  "data": {
    "transferId": "<생성된 transferId>",
    "transferStatus": "SUCCESS",
    "requestedAt": "<timestamp>"
  }
}
```

**사후 검증 — transaction-server DB:**

```sql
-- Saga 상태 확인 (SUCCESS)
SELECT saga_id, transaction_type, saga_status, current_step
FROM saga_transaction
ORDER BY started_at DESC
FETCH FIRST 1 ROWS ONLY;

-- 단계별 이력 확인 (3건: BANK_TRANSFER_REQUEST_CREATED / STOCK_CASH_DEPOSIT / BANK_TRANSFER_COMMIT)
SELECT step_name, step_order, step_status
FROM saga_step_history
WHERE saga_id = (SELECT MAX(saga_id) FROM saga_transaction)
ORDER BY step_order;

-- Outbox 이벤트 확인 (saga.started, saga.completed)
SELECT event_type, topic_name, published_yn
FROM outbox_event
ORDER BY created_at DESC
FETCH FIRST 5 ROWS ONLY;
```

**사후 검증 — bank-server DB:**

```sql
-- 잔액 차감 확인 (5,000,000 - 500,000 = 4,500,000)
SELECT account_id, balance FROM bank_account WHERE account_id = 1077;
```

**사후 검증 — stock-server DB:**

```sql
-- 예수금 증가 확인 (50,000,000 + 500,000 = 50,500,000)
SELECT securities_account_id, cash_balance
FROM securities_account
WHERE account_number = '300-777-000071';
```

---

## SAGA-BTS-002. 멱등성 — 동일 Idempotency-Key 재요청

> 동일 키로 두 번 요청하면 동일한 응답을 반환하고 이중 처리하지 않습니다.

```bash
# 첫 번째 요청
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-bts-idem-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1077,
    "toBankCode": "039",
    "toAccountNumber": "300-777-000071",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .

# 동일 키로 재요청 (동일 응답 반환, 추가 처리 없음)
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-bts-idem-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1077,
    "toBankCode": "039",
    "toAccountNumber": "300-777-000071",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .
```

**검증 포인트:** 두 응답의 `transferId`가 동일한지, saga_transaction이 1건만 생성됐는지 확인

```sql
-- idempotency_key 저장 확인
SELECT idempotency_key, request_type, status
FROM idempotency_key
WHERE idempotency_key = 'saga-bts-idem-001';
```

---

## SAGA-BTS-003. 에러 케이스 — 잔액 부족

> 보유 잔액보다 큰 금액 요청 시 STEP 1(이체 레코드 생성) 또는 STEP 3(approve) 단계에서 실패합니다.

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-bts-fail-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1077,
    "toBankCode": "039",
    "toAccountNumber": "300-777-000071",
    "transferAmount": 99999999,
    "requestedBy": "USER"
  }' | jq .
```

**사후 검증 — Saga 상태 확인:**

```sql
SELECT saga_id, saga_status, failure_reason
FROM saga_transaction
WHERE transaction_key = 'saga-bts-fail-001';
```

---

## SAGA-BTS-004. 에러 케이스 — Idempotency-Key 누락

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1077,
    "toBankCode": "039",
    "toAccountNumber": "300-777-000071",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .
```

---

# SAGA-2. STOCK_TO_BANK — 증권 예수금 → 은행 입금

> `fromAccountId`가 증권 계좌로 매핑되어 있으면 STOCK_TO_BANK Saga가 실행됩니다.
>
> **내부 흐름:**
> 1. STEP 1 — stock-server: 예수금 차감 (`cash_balance` 감소)
> 2. STEP 2 — bank-server: 이체 레코드 생성 (REQUESTED)
> 3. STEP 3 — bank-server: 이체 확정 (실제 입금 실행, SUCCESS)

---

## SAGA-STB-001. 정상 흐름 — 증권 예수금 출금 + 은행 입금

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-stb-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 25,
    "toBankCode": "777",
    "toAccountNumber": "110-111-000074",
    "transferAmount": 300000,
    "requestedBy": "USER"
  }' | jq .
```

**기대 응답:**

```json
{
  "success": true,
  "data": {
    "transferId": "<생성된 transferId>",
    "transferStatus": "SUCCESS",
    "requestedAt": "<timestamp>"
  }
}
```

**사후 검증 — transaction-server DB:**

```sql
-- Saga 상태 확인 (STOCK_TO_BANK, SUCCESS)
SELECT saga_id, transaction_type, saga_status
FROM saga_transaction
ORDER BY started_at DESC
FETCH FIRST 1 ROWS ONLY;

-- 단계별 이력 확인 (3건: STOCK_CASH_WITHDRAW / BANK_TRANSFER_REQUEST_CREATED / BANK_TRANSFER_COMMIT)
SELECT step_name, step_order, step_status
FROM saga_step_history
WHERE saga_id = (SELECT MAX(saga_id) FROM saga_transaction)
ORDER BY step_order;
```

**사후 검증 — stock-server DB:**

```sql
-- 예수금 차감 확인 (이전 잔액 - 300,000)
SELECT securities_account_id, cash_balance
FROM securities_account
WHERE account_number = '300-777-000071';
```

**사후 검증 — bank-server DB:**

```sql
-- 입금 확인 (1077 계좌 잔액 증가)
SELECT account_id, balance FROM bank_account WHERE account_id = 1077;
```

---

## SAGA-STB-002. 멱등성 — 동일 Idempotency-Key 재요청

```bash
# 첫 번째 요청
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-stb-idem-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 25,
    "toBankCode": "777",
    "toAccountNumber": "110-111-000074",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .

# 동일 키로 재요청
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-stb-idem-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 25,
    "toBankCode": "777",
    "toAccountNumber": "110-111-000074",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .
```

---

## SAGA-STB-003. 에러 케이스 — 증권 예수금 부족

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: saga-stb-fail-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 25,
    "toBankCode": "777",
    "toAccountNumber": "110-111-000074",
    "transferAmount": 999999999,
    "requestedBy": "USER"
  }' | jq .
```

**사후 검증 — Saga FAILED 확인 (STEP 1 실패, 보상 없음):**

```sql
SELECT saga_id, saga_status, failure_reason
FROM saga_transaction
WHERE transaction_key = 'saga-stb-fail-001';

-- step_status FAILED 확인 (STOCK_CASH_WITHDRAW)
SELECT step_name, step_status, error_message
FROM saga_step_history
WHERE saga_id = (
  SELECT saga_id FROM saga_transaction WHERE transaction_key = 'saga-stb-fail-001'
);
```

---

# SAGA-3. Bank-to-Bank — 기존 은행 내부 이체

> `fromAccountId`가 은행 계좌이고 `toAccountNumber`가 증권 계좌번호와 일치하지 않으면 기존 bank-to-bank 이체로 처리됩니다.

---

## BANK-TRANSFER-001. 정상 흐름 — 은행 계좌 간 이체

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: bank-btb-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1077,
    "toBankCode": "777",
    "toAccountNumber": "110-111-000075",
    "transferAmount": 200000,
    "requestedBy": "USER"
  }' | jq .
```

> Saga 없이 bank-server가 직접 처리합니다. `saga_transaction` 레코드가 생성되지 않습니다.

**사후 검증:**

```sql
-- transfer_user_mapping 생성 확인
SELECT transfer_id, x_user_id FROM transfer_user_mapping ORDER BY transfer_id DESC FETCH FIRST 1 ROWS ONLY;
```

---

## BANK-TRANSFER-002. 이체 결과 조회

> `transferId`는 BANK-TRANSFER-001 응답에서 확인

```bash
TRANSFER_ID=<이체 응답의 transferId>

curl -s -X GET "http://localhost:8083/baas/v1/bank/transfers/${TRANSFER_ID}" | jq .
```

---

# SAGA-4. DB 상태 전체 조회

> 테스트 진행 후 Saga 상태를 한눈에 확인하는 쿼리 모음입니다.

---

## 전체 Saga 목록

```sql
-- transaction-server DB
SELECT saga_id,
       transaction_type,
       transaction_key,
       saga_status,
       started_at,
       completed_at
FROM saga_transaction
ORDER BY started_at DESC;
```

---

## 특정 Saga 단계 이력

```sql
-- <saga_id>를 실제 값으로 교체
SELECT step_name,
       step_order,
       step_status,
       error_message,
       processed_at
FROM saga_step_history
WHERE saga_id = <saga_id>
ORDER BY step_order;
```

---

## Outbox 이벤트 발행 현황

```sql
SELECT event_type,
       topic_name,
       published_yn,
       retry_count,
       created_at
FROM outbox_event
ORDER BY created_at DESC
FETCH FIRST 20 ROWS ONLY;
```

---

## 감사 로그 조회

```sql
SELECT transaction_type,
       transaction_key,
       transaction_status,
       source_system,
       target_system,
       audited_at
FROM transaction_audit_log
ORDER BY audited_at DESC
FETCH FIRST 10 ROWS ONLY;
```

---

## Dead Letter 이벤트 조회

```sql
SELECT dead_letter_id,
       original_topic,
       error_message,
       retry_count,
       resolved_yn,
       created_at
FROM dead_letter_event
ORDER BY created_at DESC;
```

---

# 주요 에러 코드 정리

| 코드 | 상황 | 발생 케이스 |
|---|---|---|
| `MAPPING_001` | accountId에 해당하는 user_account_mapping 없음 | fromAccountId가 매핑 안 된 경우 |
| `TRANSFER_002` | 잔액 부족 | bank 출금 / stock 예수금 부족 |
| `SAGA_001` | Saga 처리 실패 | 각 Step 실패 |
| `SAGA_002` | 증권 계좌 없음 | toAccountNumber가 본인 증권 계좌와 불일치 |
| `DUPLICATE_REQUEST` | 동일 Idempotency-Key 처리 중 중복 요청 | 처리 중인 키로 재요청 |
| `BANK_CORE_ERROR` | bank-server 내부 오류 | bank-server 장애 |
| `STOCK_CORE_ERROR` | stock-server 내부 오류 | stock-server 장애 |

---

# 추천 테스트 순서

```
[BANK_TO_STOCK 흐름]
1.  SAGA-BTS-001  → 정상 BANK_TO_STOCK (500,000원)
                    bank 1077 잔액: 5,000,000 → 4,500,000 확인
                    stock 25 예수금: 50,000,000 → 50,500,000 확인
                    saga_transaction: SUCCESS 확인
                    saga_step_history: 3건 SUCCESS 확인
2.  SAGA-BTS-002  → 동일 키(saga-bts-idem-001) 재요청 → 동일 transferId 반환 확인
3.  SAGA-BTS-003  → 잔액 부족 → saga_transaction FAILED 확인

[STOCK_TO_BANK 흐름]
4.  SAGA-STB-001  → 정상 STOCK_TO_BANK (300,000원)
                    stock 25 예수금 차감 확인
                    bank 1077 잔액 증가 확인
                    saga_transaction: STOCK_TO_BANK, SUCCESS 확인
5.  SAGA-STB-002  → 동일 키(saga-stb-idem-001) 재요청 → 동일 transferId 반환 확인
6.  SAGA-STB-003  → 예수금 부족 → saga_transaction FAILED 확인 (보상 없음)

[Bank-to-Bank 이체]
7.  BANK-TRANSFER-001 → 1077 → 1078 이체 (200,000원)
                          saga_transaction 생성 없음 확인
8.  BANK-TRANSFER-002 → 이체 결과 조회

[공통 사후 검증]
9.  Outbox 이벤트 발행 현황 조회 (saga.started / saga.completed 확인)
10. 감사 로그 조회 (BANK_TO_STOCK / STOCK_TO_BANK 기록 확인)
```

---

## Windows PowerShell 참고

```powershell
# BANK_TO_STOCK 정상 이체
curl.exe -s -X POST "http://localhost:8083/baas/v1/bank/transfers" `
  -H "Idempotency-Key: saga-bts-001" `
  -H "Content-Type: application/json" `
  -d '{\"fromAccountId\":1077,\"toBankCode\":\"039\",\"toAccountNumber\":\"300-777-000071\",\"transferAmount\":500000,\"requestedBy\":\"USER\"}'
```

```powershell
# STOCK_TO_BANK 정상 이체
curl.exe -s -X POST "http://localhost:8083/baas/v1/bank/transfers" `
  -H "Idempotency-Key: saga-stb-001" `
  -H "Content-Type: application/json" `
  -d '{\"fromAccountId\":25,\"toBankCode\":\"777\",\"toAccountNumber\":\"110-111-000074\",\"transferAmount\":300000,\"requestedBy\":\"USER\"}'
```

```powershell
# 이체 결과 조회
$TRANSFER_ID = "<transferId>"
curl.exe -s -X GET "http://localhost:8083/baas/v1/bank/transfers/$TRANSFER_ID" | jq .
```
