# Stock API 테스트 문서

> **Transaction Server** `localhost:8083`
> 모든 요청은 transaction-server → stock-server(8082) 로 OpenFeign 프록시됩니다.

---

## 테스트 사전 조건

- git bash에 `winget install jqlang.jq` 설치

### 필수 서비스 실행 확인

```bash
# transaction-server 헬스 체크
curl -s http://localhost:8083/actuator/health

# stock-server 헬스 체크
curl -s http://localhost:8082/internal/v1/stock/health
```

---

## 공통 테스트 데이터

> stock-server DB에 아래 값이 seeding 되어 있다고 가정합니다.

### 사용자

| 항목 | 값 |
|---|---|
| userId | `1` |
| x_user_id | `1` |

### 증권 계좌 (stock-server DB)

| 항목 | 값 |
|---|---|
| accountId | `2001` |
| accountNumber | `300-123-456789` |
| bankCode | `039` (한국투자증권) |
| cashBalance | `3,000,000` |

### 종목

| 종목명 | stockCode | 시장 | 현재가 |
|---|---|---|---|
| 삼성전자 | `005930` | KOSPI | `82,000` |
| SK하이닉스 | `000660` | KOSPI | `185,000` |

### 주문/이체 ID (테스트 중 생성된 값 기록용)

| 항목 | 값 |
|---|---|
| transferId | *(이체 실행 후 응답에서 확인)* |
| orderId | *(주문 생성 후 응답에서 확인)* |

### transaction-server DB 사전 데이터 (user_account_mapping)

> transaction-server가 accountId → x_user_id 를 조회하려면 아래 데이터가 필요합니다.

```sql
-- 증권 계좌 매핑
INSERT INTO user_account_mapping (account_id, account_type, user_id, x_user_id)
VALUES (2001, 'STOCK', 1, 1)
ON CONFLICT (account_id, account_type) DO NOTHING;

-- 은행 계좌 매핑 (bank API 테스트 시 필요)
INSERT INTO user_account_mapping (account_id, account_type, user_id, x_user_id)
VALUES (1001, 'BANK', 1, 1)
ON CONFLICT (account_id, account_type) DO NOTHING;
```

---

## 공통 헤더 설명

| 헤더 | 설명 | 적용 대상 |
|---|---|---|
| `Idempotency-Key` | 중복 방지 키 | 이체 실행 / 주문 생성 / 주문 취소 |

> `X-User-Id`, `X-Trace-Id` 는 transaction-server 내부에서 자동 처리되므로 외부에서 전달하지 않습니다.

---

# STOCK API — 조회

---

## STOCK-SEARCH-001. 종목 검색

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/search?keyword=삼성" | jq .
```

**종목 코드로 검색:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/search?keyword=005930" | jq .
```

---

## STOCK-PRICE-001. 현재가 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/005930/price" | jq .
```

**SK하이닉스:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/000660/price" | jq .
```

---

## STOCK-CHART-001. 차트 조회

**일봉:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/005930/charts?interval=DAILY&fromDate=2026-05-01&toDate=2026-05-31" | jq .
```

**주봉:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/005930/charts?interval=WEEKLY" | jq .
```

**월봉:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/005930/charts?interval=MONTHLY" | jq .
```

---

## STOCK-ACCOUNT-001. 주문 가능 계좌 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts" | jq .
```

---

## STOCK-ACCOUNT-002. 예수금 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/cash-balance" | jq .
```

---

## STOCK-HOLDING-001. 보유 종목 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/holdings" | jq .
```

---

## STOCK-EXECUTION-001. 체결 내역 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/executions" | jq .
```

**종목 코드 + 날짜 필터:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/executions?stockCode=005930&fromDate=2026-05-01&toDate=2026-05-31&page=0&size=10" | jq .
```

---

## STOCK-RETURN-001. 수익률 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/returns" | jq .
```

---

# STOCK API — 주문

---

## STOCK-ORDER-001. 주문 생성

**지정가 매수:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "quantity": 5,
    "price": 82000
  }' | jq .
```

**시장가 매수 (price 없음):**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "MARKET",
    "quantity": 1
  }' | jq .
```

**지정가 매도:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "SELL",
    "orderMethod": "LIMIT",
    "quantity": 3,
    "price": 85000
  }' | jq .
```

**에러 케이스 — Validation 실패 (quantity 누락):**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT"
  }' | jq .
```

**에러 케이스 — Idempotency-Key 누락:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "quantity": 5,
    "price": 82000
  }' | jq .
```

---

## STOCK-ORDER-002. 주문 취소

> `orderId`는 STOCK-ORDER-001 응답에서 확인

```bash
ORDER_ID=1001

curl -s -X POST "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}/cancel" \
  -H "Idempotency-Key: $(uuidgen)" | jq .
```

---

## STOCK-ORDER-003. 주문 목록 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/orders" | jq .
```

**매수 주문만:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/orders?orderType=BUY&page=0&size=10" | jq .
```

**상태별 필터:**

```bash
# 요청 상태
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/orders?status=REQUESTED" | jq .

# 체결 완료
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/orders?status=FILLED" | jq .

# 부분 체결
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/orders?status=PARTIAL_FILLED" | jq .

# 취소
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/orders?status=CANCELLED" | jq .
```

---

## STOCK-ORDER-004. 주문 상세 조회

```bash
ORDER_ID=1001

curl -s -X GET "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}" | jq .
```

---

## 시나리오 테스트 — 전체 흐름

### 시나리오 1. 은행 이체 전체 흐름

```bash
# Step 1. 이체 실행
TRANSFER_RESP=$(curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1001,
    "toBankCode": "020",
    "toAccountNumber": "301-0987-1234",
    "transferAmount": 50000,
    "requestedBy": "USER"
  }')
echo $TRANSFER_RESP | jq .
TRANSFER_ID=$(echo $TRANSFER_RESP | jq -r '.data.transferId')

# Step 2. 이체 승인
curl -s -X POST "http://localhost:8083/baas/v1/bank/transfers/${TRANSFER_ID}/approve" | jq .

# Step 3. 이체 결과 확인
curl -s -X GET "http://localhost:8083/baas/v1/bank/transfers/${TRANSFER_ID}" | jq .

# Step 4. 계좌 잔액 확인
curl -s -X GET "http://localhost:8083/baas/v1/bank/accounts/1001" | jq '.data.balance'
```

---

### 시나리오 2. 주식 매수 → 보유 종목 확인 흐름

```bash
# Step 1. 예수금 확인
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/cash-balance" | jq '.data'

# Step 2. 현재가 확인
curl -s -X GET "http://localhost:8083/baas/v1/stock/005930/price" | jq '.data.currentPrice'

# Step 3. 매수 주문
ORDER_RESP=$(curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "quantity": 5,
    "price": 82000
  }')
echo $ORDER_RESP | jq .
ORDER_ID=$(echo $ORDER_RESP | jq -r '.data.orderId')

# Step 4. 주문 상태 확인
curl -s -X GET "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}" | jq '.data.status'

# Step 5. 보유 종목 확인
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/2001/holdings" | jq '.data.content[]'
```

---

### 시나리오 3. 주문 생성 → 취소 흐름

```bash
# Step 1. 주문 생성
ORDER_RESP=$(curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/2001/orders" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "000660",
    "orderType": "BUY",
    "orderMethod": "LIMIT",
    "quantity": 1,
    "price": 180000
  }')
echo $ORDER_RESP | jq .
ORDER_ID=$(echo $ORDER_RESP | jq -r '.data.orderId')

# Step 2. 주문 취소
curl -s -X POST "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}/cancel" \
  -H "Idempotency-Key: $(uuidgen)" | jq .

# Step 3. 취소 결과 확인
curl -s -X GET "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}" | jq '.data.status'
```

---

## 주요 에러 코드 정리

| 코드 | 상황 | 발생 API |
|---|---|---|
| `MAPPING_001` | accountId / transferId / orderId 에 해당하는 사용자 매핑 없음 | 전체 |
| `ACCOUNT_001` | 계좌 없음 | bank accounts |
| `ACCOUNT_002` | 타인 계좌 접근 | bank accounts |
| `ACCOUNT_003` | 계좌 상태 이상 (LOCKED/CLOSED) | transfer |
| `TRANSFER_001` | 이체 건 없음 | transfers |
| `TRANSFER_002` | 잔액 부족 | transfers |
| `TRANSFER_003` | 이미 처리된 이체 | transfers approve |
| `TRANSFER_004` | 타인 이체 건 접근 | transfers GET |
| `ORDER_001` | 주문 가능 금액 부족 | orders |
| `ORDER_002` | 보유 수량 부족 | orders (SELL) |
| `STOCK_CORE_ERROR` | stock-server 내부 오류 | stock 전체 |
| `BANK_CORE_ERROR` | bank-server 내부 오류 | bank 전체 |
