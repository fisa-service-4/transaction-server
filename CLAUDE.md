# CLAUDE.md

## 1. 서비스 개요
프리랜서 특화 AI 자산관리 플랫폼
불규칙한 수입을 가진 프리랜서를 위한 통합 금융/투자 관리 서비스

**주요 기능**
- 통합 자산 조회 (은행 / 증권)
- 가상 월급 설정 및 예산 관리
- AI 기반 소비 / 투자 분석
- 마이데이터 기반 금융 데이터 수집
- 이상 거래 탐지 및 알림

---

## 2. 해당 Repository 설명

**역할**
서비스 전체 거래 이벤트 및 금융 트랜잭션 처리 서버
Kafka 기반 이벤트 처리와 분산 트랜잭션 관리 수행

**주요 기능**
- Saga 기반 분산 트랜잭션 관리
- Transactional Outbox 패턴으로 이벤트 발행 보장
- 정합성 검증 (Reconciliation)
- Dead Letter 처리 (Kafka 실패 메시지 재처리)
- 감사 로그 기록

---

## 3. 기술 스택
| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 3.x, JPA |
| DB | PostgreSQL 16, Redis 7.2 |
| Message | Kafka 3.8 |
| Infra | Docker, Docker Compose |

---

## 4. 폴더 구조
```
app-server/
├─ src/
│  ├─ main/
│  │  ├─ java/com/app/
│  │  │  ├─ domain/
│  │  │  │  ├─ saga/
│  │  │  │  ├─ outbox/
│  │  │  │  ├─ reconciliation/
│  │  │  │  └─ deadletter/
│  │  │  │
│  │  │  ├─ global/
│  │  │  │  ├─ config/
│  │  │  │  ├─ exception/
│  │  │  │  ├─ response/
│  │  │  │  └─ util/
│  │  │  │
│  │  │  └─ AppServerApplication.java
│  │  │
│  │  └─ resources/
│  │
│  └─ test/
│
├─ docs/
├─ docker/
└─ CLAUDE.md
```

---

## 5. 개발 규칙

**코드 스타일**
- Spotless 적용 필수
- SonarLint 경고 제거 후 커밋
- Layered Architecture 준수
- 네이밍: 클래스 PascalCase / 메서드 camelCase / 상수 UPPER_SNAKE_CASE

**API / DB**
- 모든 응답은 공통 Response 포맷 사용
- Swagger 문서 작성 필수
- 에러 코드는 error-code.md 기준 사용
- created_at / updated_at 기본 포함
- DB 변경 시 md 문서 수정 필수

**이벤트**
- 이벤트 스키마 변경 시 전체 서버 영향도 확인
- Kafka Consumer 멱등성 보장

---

## 6. 절대 하지 말 것

**Git**
- main / develop 직접 push 금지
- force push 금지
- 리뷰 없이 merge 금지

**보안**
- API Key 하드코딩 금지
- .env 커밋 금지
- 개인정보 로그 출력 금지
- 금융 데이터 평문 저장 금지

**코드**
- System.out.println 커밋 금지
- TODO 남긴 채 merge 금지

---

## 7. 참조 문서
| 파일 | 언제 참조 |
| --- | --- |
| @docs/architecture-index.md | 시스템 구조 파악할 때 |
| @docs/api/api-index.md | API 개발 시 |
| @docs/db/db-index.md | DB 작업 시 |
| @docs/convention/git-convention.md | 브랜치/커밋/PR 규칙 확인할 때 |
| @docs/tech-stack/tech-stack.md | 기술 스택 확인할 때 |