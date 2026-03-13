# SecondHand Platform

중고 물품 거래 + 경매 기능을 함께 제공하는 Spring Boot 기반 백엔드 프로젝트입니다.

## 프로젝트 흐름

이 프로젝트는 아래 사용자 흐름을 기준으로 구현되어 있습니다.

1. 회원가입 / 로그인
2. 상품 등록 (일반 판매 또는 경매 상품)
3. 경매 조회 및 상태 동기화
4. 입찰
5. 찜(Like)
6. 상품 QnA

---

## 기술 스택

- Java 21
- Spring Boot 4.0.3
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Swagger (springdoc-openapi)
- Cloudflare R2 (S3 호환 SDK)

---

## 도메인 구조

```text
src/main/java/com/example/auction/domain
├── user
├── product
├── auction
├── bid
├── like
└── qna
```

---

## 환경 변수 설정

이 프로젝트는 `application.yml`에서 환경 변수를 읽습니다.

- 설정 파일: `src/main/resources/application.yml`
- 로컬 환경 변수 파일: `.env.local` (Git 추적 제외 권장)
- 예시 파일: `.env.example`

### 1) 예시 파일 복사

```bash
cp .env.example .env.local
```

### 2) 값 입력

`.env.local`에 실제 값을 넣어주세요.

필수 항목:

- `SPRING_PROFILES_ACTIVE`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION`
- `R2_ENDPOINT`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_BUCKET_NAME`, `R2_PUBLIC_URL`

---

## 실행 방법

```bash
./gradlew bootRun
```

기본 포트: `8080`

---

## API 문서 (Swagger)

애플리케이션 실행 후:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## 주요 API 요약

### User

- `POST /api/users/signup` : 회원가입
- `POST /api/users/login` : 로그인(JWT 발급)
- `POST /api/users/logout` : 로그아웃
- `GET /api/users/me` : 내 정보 조회
- `PATCH /api/users/me` : 내 정보 수정
- `DELETE /api/users/me` : 회원 탈퇴 (소프트 삭제)

### Product

- `POST /api/products` : 상품 등록 (multipart)
- `GET /api/products` : 상품 목록 조회
- `GET /api/products/{productId}` : 상품 상세 조회
- `PATCH /api/products/{productId}` : 상품 수정
- `PATCH /api/products/{productId}/status` : 상태 변경
- `DELETE /api/products/{productId}` : 상품 삭제

### Auction

- `GET /api/auctions/{auctionId}` : 경매 단건 조회
- `GET /api/auctions/product/{productId}` : 상품별 경매 조회
- `GET /api/auctions/status/{status}` : 상태별 경매 목록
- `PATCH /api/auctions/{auctionId}/cancel` : 경매 취소
- `PATCH /api/auctions/{auctionId}/sync` : 경매 상태 동기화

### Bid

- `POST /api/auctions/{auctionId}/bids` : 입찰
- `GET /api/auctions/{auctionId}/bids` : 경매별 입찰 목록
- `GET /api/auctions/{auctionId}/bids/highest` : 최고 입찰 조회
- `GET /api/auctions/{auctionId}/bids/count` : 입찰 수 조회
- `GET /api/auctions/bids/me` : 내 입찰 목록

### Like

- `POST /api/products/{productId}/likes` : 찜 등록
- `DELETE /api/products/{productId}/likes` : 찜 취소
- `GET /api/products/{productId}/likes/count` : 찜 수 조회
- `GET /api/likes/products/{productId}/me` : 내 찜 여부 조회
- `GET /api/likes/me` : 내 찜 목록

### QnA

- `POST /api/products/{productId}/qna` : 질문 등록
- `GET /api/products/{productId}/qna` : 상품 QnA 목록 조회
- `GET /api/qna/me` : 내 질문 목록 조회
- `PATCH /api/qna/{qnaId}/answer` : 판매자 답변 등록

---

## 현재 구현 메모

- 인증: JWT 기반
- 이미지 업로드: Cloudflare R2
- 경매 상태 전환: 현재는 `/sync` API 호출 기반
  - (`READY -> RUNNING -> FINISHED`)
- 회원 탈퇴: 하드 삭제가 아닌 상태값 변경 방식

---

## 브랜치 가이드 (권장)

```text
dev                 : 통합 개발 브랜치
feat/*              : 기능 브랜치 (예: feat/auction, feat/like)
hotfix/*            : 긴급 수정
```

---

## 향후 개선 아이디어

- 경매 상태 자동 전환 스케줄러 (`@Scheduled`) 도입
- 입찰 동시성 제어 (낙관/비관 락)
- 공통 응답 포맷 표준화
- 테스트 코드 확장 (서비스/통합 테스트)
- 운영 환경 Secret Manager 연동

