# Stock API 테스트 문서

> **Transaction Server** `localhost:8083`
> 모든 요청은 transaction-server → stock-server(8082) 로 OpenFeign 프록시됩니다.

---

## 테스트 사전 조건

- git bash에 `winget install jqlang.jq` 설치
- stock-server up

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
| accountId | `1` |
| accountNumber | `1234567890` |
| bankCode | `KIS` |
| cashBalance | `100,000,000` |

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

### transaction-server DB 사전 데이터

> data.sql로 자동 seeding됩니다. 수동으로 넣어야 할 경우 아래 참고.

**user_master** (firebase UID → x_user_id 매핑)

```sql
MERGE INTO user_master t
USING (SELECT 1 AS user_id, 1 AS x_user_id, '홍길동' AS user_name, '01012341234' AS phone_number FROM DUAL) s
ON (t.user_id = s.user_id)
WHEN NOT MATCHED THEN INSERT (user_id, x_user_id, user_name, phone_number)
VALUES (s.user_id, s.x_user_id, s.user_name, s.phone_number);
```

**user_account_mapping** (accountId → x_user_id 매핑)

```sql
-- 증권 계좌 매핑
MERGE INTO user_account_mapping t
USING (SELECT 1 AS account_id, 'STOCK' AS account_type, 1 AS user_id, 1 AS x_user_id FROM DUAL) s
ON (t.account_id = s.account_id AND t.account_type = s.account_type)
WHEN NOT MATCHED THEN INSERT (account_id, account_type, user_id, x_user_id)
VALUES (s.account_id, s.account_type, s.user_id, s.x_user_id);
```

---

## 공통 헤더 설명

| 헤더 | 설명 | 적용 대상 |
|---|---|---|
| `X-Firebase-Uid` | 사용자 식별 (firebase UID) | 사용자 단위 목록 조회 |
| `Idempotency-Key` | 중복 방지 키 | 이체 실행 / 주문 생성 / 주문 취소 |

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
    "firebaseUid": "firebase-test-uid-001",
    "name": "홍길동",
    "phoneNumber": "01012341234"
  }' | jq .
```

**에러 케이스 — 사용자 없음:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/user/link" \
  -H "Content-Type: application/json" \
  -d '{
    "firebaseUid": "firebase-unknown",
    "name": "없는사람",
    "phoneNumber": "01099999999"
  }' | jq .
```

---

# STOCK API — 조회

---

## STOCK-SEARCH-001. 종목 검색 -> 현재 한글 검색은 안됨 ㅜ

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/search?keyword=NAVER" | jq .
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

> Link API로 firebase_uid 등록 후 사용 가능합니다.

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts" \
  -H "X-Firebase-Uid: firebase-test-uid-001" | jq .
```

---

## STOCK-ACCOUNT-002. 예수금 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/cash-balance" | jq .
```

---

## STOCK-HOLDING-001. 보유 종목 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/holdings" | jq .
```

---

## STOCK-EXECUTION-001. 체결 내역 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/executions" | jq .
```

**종목 코드 + 날짜 필터:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/executions?stockCode=005930&fromDate=2026-05-01&toDate=2026-05-31&page=0&size=10" | jq .
```

---

## STOCK-RETURN-001. 수익률 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/returns" | jq .
```

---

# STOCK API — 주문

---

## STOCK-ORDER-001. 주문 생성

**지정가 매수:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/1/orders" \
  -H "Idempotency-Key: test-key-123" \
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
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/1/orders" \
  -H "Idempotency-Key: test-key-123" \
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
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/1/orders" \
  -H "Idempotency-Key: test-key-123" \
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
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/1/orders" \
  -H "Idempotency-Key: test-key-123" \
  -H "Content-Type: application/json" \
  -d '{
    "stockCode": "005930",
    "orderType": "BUY",
    "orderMethod": "LIMIT"
  }' | jq .
```

**에러 케이스 — Idempotency-Key 누락:**

```bash
curl -s -X POST "http://localhost:8083/baas/v1/stock/accounts/1/orders" \
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
ORDER_ID=29

curl -s -X POST "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}/cancel" \
  -H "Idempotency-Key: test-key-123" | jq .
```

---

## STOCK-ORDER-003. 주문 목록 조회

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/orders" | jq .
```

**매수 주문만:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/orders?orderType=BUY&page=0&size=10" | jq .
```

**상태별 필터:**

```bash
# 요청 상태
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/orders?status=REQUESTED" | jq .

# 체결 완료
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/orders?status=FILLED" | jq .

# 부분 체결
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/orders?status=PARTIAL_FILLED" | jq .

# 취소
curl -s -X GET "http://localhost:8083/baas/v1/stock/accounts/1/orders?status=CANCELLED" | jq .
```

---

## STOCK-ORDER-004. 주문 상세 조회

```bash
ORDER_ID=28

curl -s -X GET "http://localhost:8083/baas/v1/stock/orders/${ORDER_ID}" | jq .
```

---

## 주요 에러 코드 정리

| 코드 | 상황 | 발생 API |
|---|---|---|
| `USER_001` | firebase_uid에 해당하는 사용자 없음 (Link API 미완료) | 사용자 단위 목록 조회 |
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
