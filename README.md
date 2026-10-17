<div align="center">

<img src="frontend/public/kino.png" width="88" alt="KINO">

### KINO

영화관 예매의 전 과정을 구현한 풀스택 프로젝트<br>
실시간 좌석 선점부터 PG 결제, 티켓 발급까지

<br>

<img src="docs/images/reservation.png" width="860" alt="좌석 선택 화면">

</div>

<br>
---

## About My Contribution

이 저장소는 3인 팀으로 개발한 영화 예매 서비스 KINO의 개인 포트폴리오용 Fork입니다.

저는 결제 도메인을 중심으로 토스페이먼츠 PG 연동,
쿠폰·포인트 처리, 마이페이지 및 예매 내역 기능을 담당했습니다.

특히 외부 결제 승인과 내부 데이터베이스 처리가
하나의 트랜잭션으로 묶이지 않는 문제를 고려하여,
중복 승인 방지와 보상 취소 로직을 구현했습니다.

| 구분 | 내용 |
|---|---|
| 프로젝트 기간 | 2026.01 ~ 2026.03 |
| 참여 인원 | 3명 |
| 담당 역할 | 결제 · 쿠폰 · 포인트 · 마이페이지 |
| 주요 기술 | Java 17, Spring Boot, JPA, MySQL, Toss Payments, React |

### 담당 기능

| 영역 | 구현 내용 |
|---|---|
| Payment | 결제 준비, 승인, 취소 API 및 토스페이먼츠 연동 |
| Payment Consistency | 서버 금액 재검증, 중복 승인 방지, 보상 취소 |
| Coupon | 쿠폰 등록, 조회, 적용 및 사용 상태 관리 |
| Point | 포인트 사용, 적립 및 잔액 검증 |
| My Page | 마이페이지, 예매 내역 조회 및 취소 |

---

## Technical Highlights

### 01. 외부 결제 승인과 DB 트랜잭션의 일관성

**문제**

토스페이먼츠에서 결제가 승인되더라도,
내부 DB의 예매 확정이나 쿠폰·포인트 후속 처리 과정에서
예외가 발생할 수 있습니다.

이 경우 외부 결제는 성공했지만 내부 예매는
완료되지 않는 불일치 상태가 발생할 수 있습니다.

**해결**

- 결제 주문에 비관적 락을 적용하여 동시 승인 요청 제어
- 이미 결제가 완료된 주문에 대해서는 중복 승인 방지
- 외부 PG 승인 후 내부 DB 처리에 실패하면 보상 취소 요청
- 결제 상태를 기준으로 중복 요청과 재시도 처리

**핵심 설계**

외부 PG와 내부 DB를 하나의 트랜잭션으로 묶을 수 없으므로,
멱등성 제어와 보상 트랜잭션을 조합하여 불일치 위험에 대응했습니다.

### 02. 클라이언트 결제 금액 검증

**문제**

클라이언트에서 전달한 결제 금액을 그대로 신뢰하면
요청 값을 변경하여 정상 가격과 다른 금액으로
결제를 시도할 위험이 있습니다.

**해결**

- 서버에서 티켓 가격과 할인 조건을 기준으로 금액 계산
- 쿠폰과 포인트 사용 조건 검증
- 서버에서 계산한 최종 금액과 승인 요청 금액 비교
- 검증에 실패한 요청은 결제 승인 전에 차단

### 03. 결제 요청의 중복 실행 방지

**문제**

React StrictMode의 이펙트 중복 실행이나
네트워크 오류 이후의 재시도로 결제 승인 API가
중복 호출될 수 있습니다.

**해결**

- 프런트엔드에서 중복 승인 요청을 제어
- 서버에서 주문 상태를 재검증
- 비관적 락을 이용해 동시 처리 구간 보호
- 이미 완료된 결제는 기존 결과를 반환하도록 처리

---

## Payment Flow

```text
사용자 결제 요청
       │
       ▼
[1] 결제 준비
    ├─ 서버 금액 계산
    ├─ 쿠폰 사용 조건 검증 및 선점
    └─ 포인트 사용 조건 검증
       │
       ▼
[2] 토스페이먼츠 결제 요청
       │
       ▼
[3] 서버 결제 승인
    ├─ 중복 요청 확인
    ├─ 결제 금액 재검증
    └─ PG 승인 요청
       │
       ▼
[4] 내부 후속 처리
    ├─ 예매 확정
    ├─ 쿠폰 사용 확정
    └─ 포인트 처리
       │
       ├── 성공 → 결제 완료
       │
       └── 실패 → PG 보상 취소 요청
```

---

## Technical Deep Dive

대표적인 기술적 문제 해결 과정은 별도 문서에서 다룹니다.

**결제 승인과 데이터 일관성**

- 문제 발생 조건과 재현 과정
- 외부 PG와 로컬 트랜잭션의 한계
- 멱등성 제어 방식과 대안 비교
- 비관적 락을 선택한 이유
- 보상 취소 구현과 남은 한계
- 실제 구현 코드 및 검증 결과

상세 문서는 작성 후 연결할 예정입니다.

---

## 이런 걸 만들었습니다

멀티플렉스 영화관의 온라인 예매 시스템을 재현했습니다. 단순 CRUD가 아니라
**여러 사람이 같은 좌석을 동시에 누르는 상황**과 **결제 도중 실패하는 상황**을
어떻게 안전하게 처리할지에 초점을 뒀습니다.

- **실시간 좌석 선점** — WebSocket으로 남이 잡은 좌석이 즉시 회색으로 바뀝니다
- **동시성 제어** — 비관적 락으로 같은 좌석 중복 예매를 차단, 10분 뒤 자동 해제
- **결제 안전장치** — 금액 위변조 검증, 멱등성 보장, 승인 후 DB 실패 시 PG 전액취소
- **회원 / 비회원 이원 인증** — JWT 기반, 카카오·네이버·구글 소셜 로그인

> [!NOTE]
> 학습용 프로젝트입니다. 결제는 토스페이먼츠 테스트 키로 동작하며 실제 결제가 발생하지 않습니다.

## 구조

<img src="docs/images/architecture.svg" width="100%" alt="시스템 아키텍처">

## 빠른 실행

> 사전 준비 — JDK 17+ · Node 20+ · MySQL 8+

```bash
mysql -u root -p -e "CREATE DATABASE kino_db DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p kino_db < frontend/common_sql.sql
mysql -u root -p kino_db < backend/src/main/resources/sql/coupons_kino_partner_seed.sql
```

```bash
cd backend && ./gradlew bootRun            # localhost:8080
```

```bash
cd frontend && npm install && npm run dev  # localhost:5173
```

API 명세는 서버 기동 후 `localhost:8080/swagger-ui/index.html` 에서 확인할 수 있습니다.

[![로컬 개발 환경](https://img.shields.io/badge/환경변수_설정_가이드-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/로컬-개발-환경)

## 기술 스택

**Backend**

![Java](https://img.shields.io/badge/Java_17-1A1A1A?style=flat-square&logo=openjdk&logoColor=B91C1C)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-1A1A1A?style=flat-square&logo=springboot&logoColor=B91C1C)
![JPA](https://img.shields.io/badge/JPA_·_Hibernate-1A1A1A?style=flat-square&logo=hibernate&logoColor=B91C1C)
![MySQL](https://img.shields.io/badge/MySQL_8-1A1A1A?style=flat-square&logo=mysql&logoColor=B91C1C)
![Spring Security](https://img.shields.io/badge/Spring_Security-1A1A1A?style=flat-square&logo=springsecurity&logoColor=B91C1C)
![JWT](https://img.shields.io/badge/JWT-1A1A1A?style=flat-square&logo=jsonwebtokens&logoColor=B91C1C)
![STOMP](https://img.shields.io/badge/WebSocket_STOMP-1A1A1A?style=flat-square&logo=socketdotio&logoColor=B91C1C)

**Frontend**

![React](https://img.shields.io/badge/React_19-1A1A1A?style=flat-square&logo=react&logoColor=B91C1C)
![TypeScript](https://img.shields.io/badge/TypeScript-1A1A1A?style=flat-square&logo=typescript&logoColor=B91C1C)
![Vite](https://img.shields.io/badge/Vite_7-1A1A1A?style=flat-square&logo=vite&logoColor=B91C1C)
![Tailwind](https://img.shields.io/badge/Tailwind_CSS_4-1A1A1A?style=flat-square&logo=tailwindcss&logoColor=B91C1C)
![shadcn/ui](https://img.shields.io/badge/shadcn%2Fui-1A1A1A?style=flat-square&logo=shadcnui&logoColor=B91C1C)

**Infra**

![Docker](https://img.shields.io/badge/Docker-1A1A1A?style=flat-square&logo=docker&logoColor=B91C1C)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-1A1A1A?style=flat-square&logo=githubactions&logoColor=B91C1C)
![AWS](https://img.shields.io/badge/AWS_EC2_·_RDS-1A1A1A?style=flat-square)

**External API**

![TossPayments](https://img.shields.io/badge/토스페이먼츠-1A1A1A?style=flat-square)
![Kakao](https://img.shields.io/badge/Kakao_OAuth-1A1A1A?style=flat-square&logo=kakao&logoColor=B91C1C)
![Naver](https://img.shields.io/badge/Naver_OAuth-1A1A1A?style=flat-square&logo=naver&logoColor=B91C1C)
![Google](https://img.shields.io/badge/Google_OAuth-1A1A1A?style=flat-square&logo=google&logoColor=B91C1C)
![Solapi](https://img.shields.io/badge/Solapi_SMS-1A1A1A?style=flat-square)
![SMTP](https://img.shields.io/badge/Gmail_SMTP-1A1A1A?style=flat-square&logo=gmail&logoColor=B91C1C)

## 문서

| 문서 | 내용 |
|:--|:--|
| [![Screens](https://img.shields.io/badge/SCREENS-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/화면) | 예매 흐름을 따라 보는 실제 화면 |
| [![Architecture](https://img.shields.io/badge/ARCHITECTURE-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/아키텍처) | 시스템 구성과 요청 흐름 |
| [![Domain](https://img.shields.io/badge/DOMAIN_MODEL-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/도메인-모델) | ERD와 좌석·예약·결제 상태 전이 |
| [![Implementation](https://img.shields.io/badge/IMPLEMENTATION-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/핵심-구현) | 동시성 제어 · 결제 안전장치 · 인증 설계 |
| [![Troubleshooting](https://img.shields.io/badge/TROUBLESHOOTING-B91C1C?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/트러블슈팅) | 막혔던 문제와 해결 과정 |
| [![API](https://img.shields.io/badge/API-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/API) | 엔드포인트 명세 |
| [![Deployment](https://img.shields.io/badge/DEPLOYMENT-1A1A1A?style=for-the-badge)](https://github.com/SO0omin/KINO/wiki/배포) | Docker 구성과 CI/CD 파이프라인 |

## 팀

세 명이 도메인을 나눠 맡고, 경계가 닿는 화면은 함께 만들었습니다.

| 담당 | 영역 |
|:--|:--|
| **정수민** [![SO0omin](https://img.shields.io/badge/@SO0omin-1A1A1A?style=flat-square&logo=github&logoColor=white)](https://github.com/SO0omin) | 좌석 지정 예매 · 실시간 좌석 동기화 · 회원·비회원 인증 · 소셜 로그인 · 극장·상영시간표 |
| **함한솔** [![h-ns-l0](https://img.shields.io/badge/@h--ns--l0-1A1A1A?style=flat-square&logo=github&logoColor=white)](https://github.com/h-ns-l0) | 결제 및 PG 연동 · 쿠폰·포인트 · 마이페이지 전 섹션 · 예매 내역 및 취소 |
| **이류진** [![ryurujxx](https://img.shields.io/badge/@ryurujxx-1A1A1A?style=flat-square&logo=github&logoColor=white)](https://github.com/ryurujxx) | 예매 필터 · 영화 목록·상세 · 리뷰 · 메인 페이지 · 박스오피스 랭킹 |
