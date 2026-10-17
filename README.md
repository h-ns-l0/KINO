<div align="center">

<img src="frontend/public/kino.png" width="88" alt="KINO">

### KINO

영화관 예매의 전 과정을 구현한 풀스택 프로젝트<br>
실시간 좌석 선점부터 PG 결제, 티켓 발급까지

<br>

<img src="docs/images/reservation.png" width="860" alt="좌석 선택 화면">

</div>

<br>

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

## My Contribution

3인 팀 프로젝트에서 결제 도메인과 마이페이지를 담당했습니다.

| 영역 | 담당 업무 |
|:--|:--|
| Payment | 토스페이먼츠 결제 준비·승인·취소 |
| Payment Consistency | 서버 금액 검증, 중복 승인 방지, 보상 취소 |
| Coupon | 쿠폰 등록·조회·적용 및 상태 관리 |
| Point | 포인트 사용·적립 및 검증 |
| My Page | 마이페이지, 예매 내역 조회 및 취소 |

> 이 저장소는 개인 포트폴리오를 위해 Fork한 저장소입니다.
> [원본 팀 프로젝트](https://github.com/SO0omin/KINO)
