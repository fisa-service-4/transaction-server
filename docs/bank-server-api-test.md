# Bank Server API 테스트 가이드

> 프론트엔드 개발자용 테스트 레퍼런스  
> Base URL: `http://localhost:8081`  
> 모든 API는 `/internal/v1/bank` 하위에 있습니다.

---

## 테스트 계정 정보

앱 최초 기동 시 자동 생성됩니다.

| 항목 | 계좌 A | 계좌 B |
|---|---|---|
| userId | `501` | `501` |
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
| `5001` | 1001 | 1002 (`088` / `110-987-654321`) | 300,000원 | SUCCESS |
| `5002` | 1001 | 외부 (`020` / `301-0987-1234`) | 500,000원 | PROCESSING |

---

## 공통 헤더

| 헤더 | 필수 | 설명 |
|---|---|---|
| `X-User-Id` | O | 테스트 계정: `501` |
| `X-Trace-Id` | O | 임의 문자열 (ex: `test-001`) |

> 이체 생성(`POST /transfers`) 시 `Idempotency-Key` 헤더를 추가해야 합니다.

---

## 1. 헬스 체크

```bash
curl http://localhost:8081/internal/v1/bank/health \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": { "database": "UP", "server": "UP" },
  "meta": { "traceId": "test-001" }
}
```

---

## 2. 정합성 검증

### 2-1. 검증 실행

> 미입력 시 전일 기준으로 실행됩니다. 계좌 잔액과 마지막 거래 원장의 `balanceAfter`를 비교합니다.

```bash
# targetDate 없이 실행 (전일 기준)
curl -X POST http://localhost:8081/internal/v1/bank/reconciliation/run \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-001" \
  -d '{}'
```

```bash
# 특정 날짜 지정
curl -X POST http://localhost:8081/internal/v1/bank/reconciliation/run \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: test-001" \
  -d '{ "targetDate": "2026-05-28" }'
```

**응답** (`202 Accepted`)
```json
{
  "success": true,
  "data": { "reconciliationId": 1, "status": "STARTED" },
  "meta": { "traceId": "test-001" }
}
```

### 2-2. 검증 결과 조회

> 실행 전에 조회하면 `status: NO_DATA`를 반환합니다.

```bash
# 최신 결과 조회
curl http://localhost:8081/internal/v1/bank/reconciliation/result \
  -H "X-Trace-Id: test-001"
```

```bash
# 특정 ID 지정
curl "http://localhost:8081/internal/v1/bank/reconciliation/result?reconciliationId=1" \
  -H "X-Trace-Id: test-001"
```

**응답 — 불일치 없음**
```json
{
  "success": true,
  "data": {
    "reconciliationId": 1,
    "status": "SUCCESS",
    "totalChecked": 3,
    "mismatchCount": 0,
    "mismatches": [],
    "executedAt": "2026-05-28T10:00:00"
  },
  "meta": { "traceId": "test-001" }
}
```

**응답 — 불일치 있음**
```json
{
  "success": true,
  "data": {
    "reconciliationId": 1,
    "status": "FAILED",
    "totalChecked": 3,
    "mismatchCount": 1,
    "mismatches": [
      {
        "entityType": "ACCOUNT",
        "entityId": 1001,
        "reason": "balance mismatch: last_tx_balance_after=4950000, account_balance=5000000"
      }
    ],
    "executedAt": "2026-05-28T10:00:00"
  },
  "meta": { "traceId": "test-001" }
}
```

---

## 3. 계좌 목록 조회

```bash
curl http://localhost:8081/internal/v1/bank/accounts \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "accountId": 1001,
        "userId": 501,
        "bankCode": "088",
        "accountNumber": "110-123-456789",
        "accountName": "급여통장",
        "balance": 5000000.00,
        "accountStatus": "ACTIVE"
      },
      {
        "accountId": 1002,
        "userId": 501,
        "bankCode": "088",
        "accountNumber": "110-987-654321",
        "accountName": "생활비통장",
        "balance": 1200000.00,
        "accountStatus": "ACTIVE"
      }
    ]
  },
  "meta": { "traceId": "test-001" }
}
```

---

## 4. 계좌 상세 조회

```bash
curl http://localhost:8081/internal/v1/bank/accounts/1001 \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답** (`openedAt` / `closedAt` / `updatedAt` 포함)
```json
{
  "success": true,
  "data": {
    "accountId": 1001,
    "userId": 501,
    "bankCode": "088",
    "accountNumber": "110-123-456789",
    "accountName": "급여통장",
    "balance": 5000000.00,
    "accountStatus": "ACTIVE",
    "openedAt": "2026-05-28T06:00:00",
    "closedAt": null,
    "updatedAt": "2026-05-28T06:00:00"
  },
  "meta": { "traceId": "test-001" }
}
```

**에러 케이스**
```json
// 계좌 없음
{ "success": false, "error": { "code": "ACCOUNT_001", "message": "계좌가 존재하지 않습니다" } }

// 본인 계좌 아님
{ "success": false, "error": { "code": "ACCOUNT_002", "message": "계좌 접근 권한이 없습니다" } }
```

---

## 5. 잔액 조회

```bash
curl http://localhost:8081/internal/v1/bank/accounts/1001/balance \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": {
    "accountId": 1001,
    "balance": 5000000.00,
    "updatedAt": "2026-05-28T06:00:00"
  },
  "meta": { "traceId": "test-001" }
}
```

---

## 6. 거래내역 조회

> 모든 필터는 선택 사항이며 자유롭게 조합할 수 있습니다. `/filter` 엔드포인트는 없습니다.

### 6-1. 전체 조회 (필터 없음)

```bash
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

### 6-2. 거래 유형 필터

```bash
# 입금만 조회
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?type=DEPOSIT" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"

# 출금만 조회
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?type=WITHDRAW" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"

# 이체 출금만 조회
curl "http://localhost:8081/internal/v1/bank/accounts/1002/transactions?type=TRANSFER_OUT" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

> `type` 허용값: `DEPOSIT` / `WITHDRAW` / `TRANSFER_IN` / `TRANSFER_OUT` / `AUTO_TRANSFER`

### 6-3. 채널 · 상태 필터

```bash
# AI_AGENT 채널만 조회
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?channel=AI_AGENT" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"

# 실패 거래만 조회
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?status=FAILED" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

> `channel` 허용값: `APP` / `AI_AGENT`  
> `status` 허용값: `SUCCESS` / `FAILED` / `CANCELLED`

### 6-4. 날짜 범위 필터

```bash
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?fromDate=2026-05-01&toDate=2026-05-31" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

### 6-5. 금액 범위 필터

```bash
# 10만원 이상 거래
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?minAmount=100000" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

### 6-6. 복합 필터 + 페이지네이션

```bash
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?type=DEPOSIT&channel=APP&status=SUCCESS&fromDate=2026-05-01&toDate=2026-05-31&page=0&size=10" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "transactionId": 9002,
        "accountId": 1001,
        "transactionType": "WITHDRAW",
        "transactionCategory": "식비",
        "amount": 50000.00,
        "balanceAfter": 4950000.00,
        "oppositeBankCode": "020",
        "oppositeAccountNumber": "301-1111-2222",
        "transactionChannel": "APP",
        "transactionStatus": "SUCCESS",
        "transactionAt": "2026-05-28T06:00:00"
      },
      {
        "transactionId": 9001,
        "accountId": 1001,
        "transactionType": "DEPOSIT",
        "transactionCategory": "급여",
        "amount": 3000000.00,
        "balanceAfter": 5000000.00,
        "oppositeBankCode": null,
        "oppositeAccountNumber": null,
        "transactionChannel": "APP",
        "transactionStatus": "SUCCESS",
        "transactionAt": "2026-05-28T06:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "meta": { "traceId": "test-001" }
}
```

---

## 7. 거래 카테고리 집계

> `fromDate`, `toDate` 모두 필수입니다.

```bash
curl "http://localhost:8081/internal/v1/bank/accounts/1001/transactions/categories?fromDate=2026-05-01&toDate=2026-05-31" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": [
    { "category": "급여", "totalAmount": 3000000.00, "count": 1 },
    { "category": "식비", "totalAmount": 50000.00,   "count": 1 }
  ],
  "meta": { "traceId": "test-001" }
}
```

---

## 8. 이체 요청

> `Idempotency-Key` 헤더가 필수입니다. 동일한 키로 재요청 시 중복 처리를 방지합니다.

```bash
curl -X POST http://localhost:8081/internal/v1/bank/transfers \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "020",
    "toAccountNumber": "301-0987-1234",
    "transferAmount": 100000,
    "requestedBy": "USER"
  }'
```

**응답** (`201 Created`)
```json
{
  "success": true,
  "data": {
    "transferId": 5003,
    "transferStatus": "REQUESTED",
    "requestedAt": "2026-05-28T10:30:00"
  },
  "meta": { "traceId": "test-001" }
}
```

**에러 케이스**
```json
// 잔액 부족
{ "success": false, "error": { "code": "TRANSFER_002", "message": "잔액이 부족합니다" } }

// 본인 계좌 아님
{ "success": false, "error": { "code": "ACCOUNT_002", "message": "계좌 접근 권한이 없습니다" } }

// 비활성 계좌
{ "success": false, "error": { "code": "ACCOUNT_003", "message": "비활성 계좌입니다" } }
```

---

## 9. 이체 상태 조회

> Saga 진행 상태 및 결과를 확인합니다.

```bash
# 시드 데이터 — SUCCESS 상태
curl http://localhost:8081/internal/v1/bank/transfers/5001 \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"

# 시드 데이터 — PROCESSING 상태
curl http://localhost:8081/internal/v1/bank/transfers/5002 \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": {
    "transferId": 5001,
    "transferStatus": "SUCCESS",
    "fromAccountId": 1001,
    "toBankCode": "088",
    "toAccountNumber": "110-987-654321",
    "transferAmount": 300000.00,
    "requestedAt": "2026-05-28T06:00:00",
    "completedAt": "2026-05-28T06:00:00"
  },
  "meta": { "traceId": "test-001" }
}
```

**에러 케이스**
```json
// 이체 건 없음
{ "success": false, "error": { "code": "TRANSFER_001", "message": "해당 이체 건을 찾을 수 없습니다" } }

// 본인 이체 건 아님
{ "success": false, "error": { "code": "TRANSFER_004", "message": "본인 이체 건이 아닙니다" } }
```

---

## 10. 이체 승인

> Saga Commit 단계입니다. 실제 서비스에서는 Transaction Server가 호출합니다.  
> `REQUESTED` / `PROCESSING` 상태인 이체만 승인 가능합니다.

```bash
# 8번에서 생성한 이체 승인 (transferId는 응답에서 확인)
curl -X POST http://localhost:8081/internal/v1/bank/transfers/5003/approve \
  -H "X-User-Id: 501" \
  -H "X-Trace-Id: test-001"
```

**응답**
```json
{
  "success": true,
  "data": {
    "transferId": 5003,
    "transferStatus": "SUCCESS",
    "completedAt": "2026-05-28T10:30:05"
  },
  "meta": { "traceId": "test-001" }
}
```

**에러 케이스**
```json
// 이미 완료된 이체 재승인
{ "success": false, "error": { "code": "TRANSFER_003", "message": "이미 처리 완료된 이체입니다" } }
```

---

## 추천 테스트 순서

처음 테스트할 때는 아래 순서로 진행하면 전체 흐름을 확인할 수 있습니다.

```
1. 헬스 체크                   → 서버 및 DB 연결 확인
2. 계좌 목록 조회               → 1001, 1002 계좌 확인 (userId 포함)
3. 계좌 상세 조회 (1001)        → openedAt / closedAt / updatedAt 확인
4. 잔액 조회 (1001)             → 초기 5,000,000원 확인
5. 거래내역 조회 (필터 없음)     → 시드 거래 2건 확인
6. 거래내역 조회 (type=DEPOSIT) → 급여 입금 1건만 조회 확인
7. 카테고리 집계                → 급여 / 식비 집계 확인
8. 이체 상태 조회 (5001)        → SUCCESS 상태 시드 이체 확인
9. 이체 요청 (새 이체 생성)      → transferId 메모
10. 이체 상태 조회 (신규)        → REQUESTED 상태 확인
11. 이체 승인                   → SUCCESS 전환 확인
12. 잔액 재조회 (1001)           → 이체 금액만큼 감소 확인
13. 정합성 검증 실행             → reconciliationId 메모
14. 정합성 검증 결과 조회        → 검증 결과 확인
```

---

## Swagger UI

전체 API 스펙은 Swagger에서 확인할 수 있습니다.

```
http://localhost:8081/swagger-ui/index.html
```

---

## Windows PowerShell 참고

PowerShell에서는 `uuidgen` 대신 `[guid]::NewGuid()`를 사용하세요.

```powershell
# 이체 요청 예시
$idempotencyKey = [guid]::NewGuid().ToString()

curl.exe -X POST http://localhost:8081/internal/v1/bank/transfers `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 501" `
  -H "X-Trace-Id: test-001" `
  -H "Idempotency-Key: $idempotencyKey" `
  -d '{\"fromAccountId\":1001,\"toBankCode\":\"020\",\"toAccountNumber\":\"301-0987-1234\",\"transferAmount\":100000,\"requestedBy\":\"USER\"}'
```

```powershell
# 거래내역 복합 필터 예시
curl.exe "http://localhost:8081/internal/v1/bank/accounts/1001/transactions?type=DEPOSIT&status=SUCCESS" `
  -H "X-User-Id: 501" `
  -H "X-Trace-Id: test-001"
```

```powershell
# 카테고리 집계 예시
curl.exe "http://localhost:8081/internal/v1/bank/accounts/1001/transactions/categories?fromDate=2026-05-01&toDate=2026-05-31" `
  -H "X-User-Id: 501" `
  -H "X-Trace-Id: test-001"
```
