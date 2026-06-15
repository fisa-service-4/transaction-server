# Card API 테스트 문서

> **Transaction Server** `localhost:8083`
> 모든 요청은 transaction-server → bank-server(8081) Card 도메인으로 OpenFeign 프록시됩니다.

---

## 테스트 사전 조건

- git bash에 `winget install jqlang.jq` 설치
- bank-server up (카드 데이터는 bank-server DB에 저장)
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

| accountId | 계좌명 | 계좌번호 |
|---|---|---|
| `1001` | 급여통장 | `110-123-456789` |
| `1002` | 생활비통장 | `110-987-654321` |

### 카드 (bank-server DB)

| cardId | 연결 계좌 | 카드번호(마스킹) | 상태 |
|---|---|---|---|
| `1` | `1001` | `1234-****-****-5678` | `ACTIVE` |
| `2` | `1002` | `9876-****-****-4321` | `ACTIVE` |

### 카드 승인 내역 (bank-server DB)

| approvalId | cardId | 가맹점 | 카테고리 | 금액 | 상태 |
|---|---|---|---|---|---|
| `50` | `1` | 스타벅스 강남점 | `CAFE` | 5,500원 | `APPROVED` |
| `51` | `1` | 이마트 | `MART` | 42,000원 | `APPROVED` |
| `52` | `1` | 네이버 플러스 | `SUBSCRIPTION` | 6,900원 | `DECLINED` |

### transaction-server DB 사전 데이터

> `data.sql`로 자동 seeding됩니다. 수동으로 넣어야 할 경우 아래 참고.

**user_account_mapping** (accountId → x_user_id 매핑)

```sql
-- 은행 계좌 1001 매핑 (카드 계좌별 목록 조회용)
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
| `X-Firebase-Uid` | 사용자 식별 | 불필요 (accountId로 사용자 자동 식별) |

> **카드 승인 내역 조회** (`/card/cards/{cardId}/approvals`) 는 현재 `xUserId = 0L` 고정으로 동작합니다.
> bank-server가 카드 소유자를 내부적으로 검증합니다.

> `X-User-Id`, `X-Trace-Id` 는 transaction-server 내부에서 자동 처리됩니다.

---

# CARD API — 카드 조회

---

## CARD-001. 계좌별 카드 목록 조회

> `accountId`로 연결된 카드 목록을 반환합니다.
> 내부적으로 `user_account_mapping`에서 x_user_id를 조회하여 bank-server에 전달합니다.

**계좌 1001 카드 목록:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/accounts/1001/cards" | jq .
```

**계좌 1002 카드 목록:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/accounts/1002/cards" | jq .
```

**에러 케이스 — 계좌 매핑 없음 (500, MAPPING_001):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/accounts/9999/cards" | jq .
```

**에러 케이스 — 계좌 없음 (404, ACCOUNT_001):**

> bank-server에 존재하지 않는 accountId를 조회하는 경우.
> transaction-server 매핑이 있어야 bank-server까지 도달합니다.

---

## CARD-002. 카드 승인 내역 조회

> `cardId`로 해당 카드의 승인/결제 이력을 페이지네이션으로 반환합니다.

**전체 조회 (필터 없음):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals" | jq .
```

**날짜 범위 필터:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?fromDate=2026-05-01&toDate=2026-05-31" | jq .
```

**승인 상태 필터:**

```bash
# 승인 내역만
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?approvalStatus=APPROVED" | jq .

# 거절 내역만
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?approvalStatus=DECLINED" | jq .

# 취소 내역만
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?approvalStatus=CANCELLED" | jq .
```

> `approvalStatus` 허용값: `APPROVED` / `DECLINED` / `CANCELLED`

**가맹점 카테고리 필터:**

```bash
# 카페 결제만
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?merchantCategory=CAFE" | jq .

# 마트 결제만
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?merchantCategory=MART" | jq .

# 구독 결제만
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?merchantCategory=SUBSCRIPTION" | jq .
```

> `merchantCategory` 허용값: `FOOD_BEVERAGE` / `CAFE` / `TRANSPORTATION` / `SHOPPING` / `MART` / `ENTERTAINMENT` / `MEDICAL` / `EDUCATION` / `SUBSCRIPTION` / `COMMUNICATION` / `BEAUTY` / `TRAVEL` / `GAS` / `ETC`

**복합 필터 + 페이지네이션:**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?fromDate=2026-05-01&toDate=2026-05-31&approvalStatus=APPROVED&merchantCategory=CAFE&page=0&size=10" | jq .
```

**에러 케이스 — 카드 없음 (404, CARD_001):**

```bash
curl -s -X GET "http://localhost:8083/baas/v1/card/cards/9999/approvals" | jq .
```

**에러 케이스 — 본인 카드 아님 (403, CARD_002):**

> bank-server가 카드 소유자 검증 실패 시 반환합니다.

---

## 주요 에러 코드 정리

| 코드 | HTTP | 상황 | 발생 API |
|---|---|---|---|
| `MAPPING_001` | 500 | accountId에 해당하는 사용자 매핑 없음 | 카드 목록 조회 |
| `ACCOUNT_001` | 404 | 계좌 없음 | 카드 목록 조회 |
| `CARD_001` | 404 | 카드 없음 | 카드 승인 내역 조회 |
| `CARD_002` | 403 | 본인 카드 아님 | 카드 승인 내역 조회 |
| `CARD_003` | 404 | 카드 승인 내역 없음 | 카드 승인 내역 조회 |
| `BANK_CORE_ERROR` | - | bank-server 내부 오류 | 전체 |

---

## 추천 테스트 순서

```
1. CARD-001 (1001)               → 계좌 1001의 카드 목록 조회 (cardId 1 확인)
2. CARD-001 (1002)               → 계좌 1002의 카드 목록 조회 (cardId 2 확인)
3. CARD-001 (9999)               → 매핑 없는 계좌 → MAPPING_001 에러 확인
4. CARD-002 (cardId=1, 필터없음) → 전체 승인 내역 조회 (3건 확인)
5. CARD-002 (approvalStatus=APPROVED)   → 승인 내역만 (2건: 50, 51)
6. CARD-002 (approvalStatus=DECLINED)   → 거절 내역만 (1건: 52)
7. CARD-002 (merchantCategory=CAFE)     → 카페 결제만 (1건: 50)
8. CARD-002 (날짜 + 카테고리 복합)      → 복합 필터 조회
9. CARD-002 (cardId=9999)               → 카드 없음 → CARD_001 에러 확인
```

---

## Windows PowerShell 참고

```powershell
# 계좌별 카드 목록 조회
curl.exe -s -X GET "http://localhost:8083/baas/v1/card/accounts/1001/cards" | jq .
```

```powershell
# 카드 승인 내역 복합 필터 조회
curl.exe -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?fromDate=2026-05-01&toDate=2026-05-31&approvalStatus=APPROVED&merchantCategory=CAFE" | jq .
```

```powershell
# 페이지네이션 적용
curl.exe -s -X GET "http://localhost:8083/baas/v1/card/cards/1/approvals?page=0&size=5" | jq .
```
