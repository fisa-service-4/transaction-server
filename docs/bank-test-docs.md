# Bank API 테스트 문서

> **Transaction Server** `localhost:8083`
> 모든 요청은 transaction-server → bank-server(8081) 로 OpenFeign 프록시됩니다.

---

## 테스트 사전 조건

- git bash에 `winget install jqlang.jq` 설치
- bank-server up
- transaction-server up

---

## 공통 테스트 데이터

> bank-server DB에 아래 값이 seeding 되어 있다고 가정합니다.

### 사용자

| 항목 | 값 |
|---|---|
| userId (bank-server) | `501` |
| x_user_id (transaction-server) | `501` |

### 은행 계좌 (bank-server DB)

| 항목 | 계좌 A | 계좌 B |
|---|---|---|
| accountId | `1001` | `1002` |
| 계좌명 | `급여통장` | `생활비통장` |
| 계좌번호 | `110-123-456789` | `110-987-654321` |
| 은행코드 | `088` | `088` |
| 초기 잔액 | `5,000,000원` | `1,200,000원` |

### 시드 거래 내역

| transactionId | 계좌 | 유형 | 카테고리 | 금액 | 채널 |
|---|---|---|---|---|---|
| `9001` | 1001 | DEPOSIT | 급여 | 3,000,000원 | APP |
| `9002` | 1001 | WITHDRAW | 식비 | 50,000원 | APP |
| `9003` | 1002 | TRANSFER_OUT | 이체 | 200,000원 | APP |

### 시드 이체 내역

| transferId | 출금 계좌 | 입금 계좌 | 금액 | 상태 |
|---|---|---|---|---|
| `5001` | 1001 | 1002 | 300,000원 | SUCCESS |
| `5002` | 1001 | 외부 (`020` / `301-0987-1234`) | 500,000원 | PROCESSING |

> 시드 이체(5001, 5002)는 transaction-server의 `data.sql`에 `transfer_user_mapping`이 함께 seeding되므로 조회 가능합니다.

### 주문/이체 ID (테스트 중 생성된 값 기록용)

| 항목 | 값 |
|---|---|
| transferId | *(이체 실행 후 응답에서 확인)* |

### transaction-server DB 사전 데이터

> `data.sql`로 자동 seeding됩니다. 수동으로 넣어야 할 경우 아래 참고.

**user_master** (firebase UID → x_user_id 매핑)

```sql
MERGE INTO user_master t
USING (SELECT 2 AS user_id, 501 AS x_user_id, 'BankUser' AS user_name, '01098765432' AS phone_number FROM DUAL) s
ON (t.user_id = s.user_id)
WHEN NOT MATCHED THEN INSERT (user_id, x_user_id, user_name, phone_number)
VALUES (s.user_id, s.x_user_id, s.user_name, s.phone_number);
```

**user_account_mapping** (accountId → x_user_id 매핑)

```sql
-- 은행 계좌 1001 매핑
MERGE INTO user_account_mapping t
USING (SELECT 1001 AS account_id, 'BANK' AS account_type, 2 AS user_id, 501 AS x_user_id FROM DUAL) s
ON (t.account_id = s.account_id AND t.account_type = s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id, account_type, user_id, x_user_id)
VALUES (s.account_id, s.account_type, s.user_id, s.x_user_id);

-- 은행 계좌 1002 매핑
MERGE INTO user_account_mapping t
USING (SELECT 1002 AS account_id, 'BANK' AS account_type, 2 AS user_id, 501 AS x_user_id FROM DUAL) s
ON (t.account_id = s.account_id AND t.account_type = s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id, account_type, user_id, x_user_id)
VALUES (s.account_id, s.account_type, s.user_id, s.x_user_id);
```

---

## 공통 헤더 설명

| 헤더 | 설명 | 적용 대상 |
|---|---|---|
| `X-Firebase-Uid` | 사용자 식별 (firebase UID) | 계좌 목록 조회 |
| `Idempotency-Key` | 중복 방지 키 | 이체 실행 |

> `X-User-Id`, `X-Trace-Id` 는 transaction-server 내부에서 자동 처리되므로 외부에서 전달하지 않습니다.
>
> `X-Firebase-Uid` 는 Link API로 firebase_uid가 등록된 사용자에 한해 사용 가능합니다.

---

# USER API

---

## USER-LINK-001. Firebase UID 연동

> 최초 로그인 시 service-backend가 호출. name + phoneNumber로 user_master 조회 후 firebase_uid 저장.

```bash
curl -s -X POST "http://localhost:8083/baas/v1/user/link" \
  -H "Content-Type: application/json" \
  -d '{
    "firebaseUid": "firebase-bank-uid-001",
    "name": "BankUser",
    "phoneNumber": "01098765432"
  }' | jq .
```

**에러 케이스 — 사용자 없음:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/user/link" \
  -H "Content-Type: application/json" \
  -d '{
    "firebaseUid": "firebase-unknown",
    "name": "NoMember",
    "phoneNumber": "01099999999"
  }' | jq .
```

---

# BANK API — 계좌 조회

---

## BANK-ACCOUNT-001. 계좌 목록 조회

> Link API로 firebase_uid 등록 후 사용 가능합니다.

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts" \
  -H "X-Firebase-Uid: firebase-bank-uid-001" | jq .
```

**상태 필터:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts?status=ACTIVE" \
  -H "X-Firebase-Uid: firebase-bank-uid-001" | jq .
```

> `status` 허용값: `ACTIVE` / `DORMANT` / `LOCKED` / `CLOSED`

---

## BANK-ACCOUNT-002. 계좌 상세 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001" | jq .
```

**생활비통장:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1002" | jq .
```

**에러 케이스 — 계좌 없음:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/9999" | jq .
```

---

## BANK-ACCOUNT-003. 거래내역 조회

**전체 조회 (필터 없음):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions" | jq .
```

**날짜 범위 필터:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions?fromDate=2026-05-01&toDate=2026-05-31" | jq .
```

**페이지네이션:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions?page=0&size=10" | jq .
```

---

## BANK-ACCOUNT-004. 거래내역 필터 조회

**거래 유형 필터:**

```bash
# 입금만 조회
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?type=DEPOSIT" | jq .

# 출금만 조회
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?type=WITHDRAW" | jq .

# 이체 출금만 조회
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1002/transactions/filter?type=TRANSFER_OUT" | jq .
```

> `type` 허용값: `DEPOSIT` / `WITHDRAW` / `TRANSFER_IN` / `TRANSFER_OUT` / `AUTO_TRANSFER`

**채널 · 상태 필터:**

```bash
# AI_AGENT 채널만 조회
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?channel=AI_AGENT" | jq .

# 실패 거래만 조회
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?status=FAILED" | jq .
```

> `channel` 허용값: `APP` / `AI_AGENT`
> `status` 허용값: `SUCCESS` / `FAILED` / `CANCELLED`

**금액 범위 필터:**

```bash
# 10만원 이상 거래
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?minAmount=100000" | jq .
```

**복합 필터 + 페이지네이션:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?type=DEPOSIT&channel=APP&status=SUCCESS&fromDate=2026-05-01&toDate=2026-05-31&page=0&size=10" | jq .
```

---

## BANK-ACCOUNT-005. 거래 카테고리 집계

> `fromDate`, `toDate` 모두 필수입니다.

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/categories?fromDate=2026-05-01&toDate=2026-05-31" | jq .
```

---

# BANK API — 이체

---

## BANK-TRANSFER-001. 이체 실행

> `Idempotency-Key` 헤더가 필수입니다. 동일한 키로 재요청 시 중복 처리를 방지합니다.

**외부 계좌로 이체:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: test-key-bank-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "020",
    "toAccountNumber": "301-0987-1234",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .
```

**내부 계좌 간 이체 (1001 → 1002):**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: test-key-bank-002" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "088",
    "toAccountNumber": "110-987-654321",
    "transferAmount": 50000,
    "requestedBy": "USER"
  }' | jq .
```

**AI 에이전트 요청 이체:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: test-key-bank-003" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "020",
    "toAccountNumber": "301-0987-1234",
    "transferAmount": 200000,
    "requestedBy": "AI"
  }' | jq .
```

**에러 케이스 — 잔액 부족:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: test-key-bank-004" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "020",
    "toAccountNumber": "301-0987-1234",
    "transferAmount": 99999999,
    "requestedBy": "USER"
  }' | jq .
```

**에러 케이스 — Idempotency-Key 누락:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "020",
    "toAccountNumber": "301-0987-1234",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }' | jq .
```

---

## BANK-TRANSFER-002. 이체 승인

> Saga Commit 단계입니다. `REQUESTED` / `PROCESSING` 상태인 이체만 승인 가능합니다.
> `transferId`는 BANK-TRANSFER-001 응답에서 확인하세요.

```bash
TRANSFER_ID=<이체 실행 응답의 transferId>

curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers/${TRANSFER_ID}/approve" | jq .
```

**에러 케이스 — 이미 완료된 이체 재승인:**

```bash
# 위에서 승인한 TRANSFER_ID 재사용
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers/${TRANSFER_ID}/approve" | jq .
```

---

## BANK-TRANSFER-003. 이체 결과 조회

**시드 이체 조회 (SUCCESS):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/transfers/5001" | jq .
```

**시드 이체 조회 (PROCESSING):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/transfers/5002" | jq .
```

**신규 생성 이체 조회:**

```bash
TRANSFER_ID=<BANK-TRANSFER-001 응답의 transferId>

curl -s -X GET "http://localhost:8083/baas/v1/bank/transfers/${TRANSFER_ID}" | jq .
```

**에러 케이스 — 이체 건 없음 (미등록 transferId):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/bank/transfers/9999" | jq .
```

---

## 주요 에러 코드 정리

| 코드 | 상황 | 발생 API |
|---|---|---|
| `USER_001` | firebase_uid에 해당하는 사용자 없음 (Link API 미완료) | 계좌 목록 조회 |
| `MAPPING_001` | accountId / transferId에 해당하는 사용자 매핑 없음 | 전체 |
| `ACCOUNT_001` | 계좌 없음 | accounts |
| `ACCOUNT_002` | 타인 계좌 접근 | accounts |
| `ACCOUNT_003` | 계좌 상태 이상 (LOCKED/CLOSED) | transfers |
| `TRANSFER_001` | 이체 건 없음 | transfers GET |
| `TRANSFER_002` | 잔액 부족 | transfers POST |
| `TRANSFER_003` | 이미 처리된 이체 | transfers approve |
| `TRANSFER_004` | 타인 이체 건 접근 | transfers GET |
| `BANK_CORE_ERROR` | bank-server 내부 오류 | bank 전체 |

---

## 추천 테스트 순서

```
1. USER-LINK-001            → firebase_uid 등록 (BankUser / 01098765432)
2. BANK-ACCOUNT-001         → 계좌 목록 조회 (1001, 1002 확인)
3. BANK-ACCOUNT-002 (1001)  → 계좌 상세 조회 (잔액 5,000,000 확인)
4. BANK-ACCOUNT-003 (1001)  → 거래내역 전체 조회 (시드 3건 확인)
5. BANK-ACCOUNT-004         → type=DEPOSIT 필터 (급여 1건만 확인)
6. BANK-ACCOUNT-005         → 카테고리 집계 (급여 / 식비 확인)
7. BANK-TRANSFER-003 (5001) → 시드 이체 조회 (SUCCESS 확인)
8. BANK-TRANSFER-003 (5002) → 시드 이체 조회 (PROCESSING 확인)
9. BANK-TRANSFER-001        → 이체 실행 → transferId 메모
10. BANK-TRANSFER-003       → 이체 결과 조회 (REQUESTED 상태 확인)
11. BANK-TRANSFER-002       → 이체 승인 (SUCCESS 전환 확인)
12. BANK-TRANSFER-003       → 이체 재조회 (SUCCESS 확인)
13. BANK-ACCOUNT-003 (1001) → 거래내역 재조회 (이체 거래 추가 확인)
```

---

## Windows PowerShell 참고

```powershell
# 이체 실행
curl.exe -s -X POST "http://localhost:8083/baas/v1/bank/transfers" `
  -H "Idempotency-Key: test-key-bank-001" `
  -H "Content-Type: application/json" `
  -d '{\"fromAccountId\":1001,\"toBankCode\":\"020\",\"toAccountNumber\":\"301-0987-1234\",\"transferAmount\":100000,\"requestedBy\":\"USER\"}'
```

```powershell
# 거래내역 복합 필터
curl.exe -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/filter?type=DEPOSIT&status=SUCCESS" | jq .
```

```powershell
# 카테고리 집계
curl.exe -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001/transactions/categories?fromDate=2026-05-01&toDate=2026-05-31" | jq .
```
