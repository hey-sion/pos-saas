# POS SaaS

소상공인 매장에서 쓰는 웹 POS와 손님용 QR 셀프 주문 기능을 함께 제공한다. 한 매장에만 묶이지 않고 다른 매장에서도 같은 코드를 그대로 쓸 수 있도록 매장 단위로 데이터가 분리되는 멀티테넌트 구조로 설계했다.

사장님은 태블릿/PC 웹에서 메뉴를 관리하고 매장에서 주문을 받는다. 손님은 매장에 붙은 QR을 찍으면 모바일에서 메뉴를 보고 직접 주문·결제까지 끝낼 수 있다. 결제는 두 갈래로 나뉜다. 사장님이 POS에서 직접 처리하는 오프라인 결제(현금, 실물 카드)와, 손님이 셀프로 처리하는 간편결제(카카오페이/PortOne 연동).

## Screens

| 매장 POS (메뉴 선택·주문 접수·고객 대기) | 손님 QR 셀프 주문 (모바일) |
|:---:|:---:|
| <img src="docs/images/pos-main.png" width="520" alt="매장 POS 화면" /> | <img src="docs/images/customer-self-order.png" width="200" alt="손님 QR 셀프 주문 화면" /> |

## Production

현재 운영 중:
- 매장 POS 페이지: `https://smilepos.kr` (로그인 필요)
- 손님 셀프 주문 페이지: `https://smilepos.kr/order/cafe-spring` (모바일용 데모 매장)

> ⚠️ 데모 매장은 **실결제 채널**입니다. PG 심사를 통과해 실제 결제까지 동작하므로, 결제를 끝까지 진행하면 실제로 청구됩니다.

## Architecture Highlights

📄 **[Case Study — 개발 의도 · 설계 의사결정 →](docs/portfolio/case-study.md)** 개발 의도와 주요 설계 의사결정은 별도 문서로 정리.

아래 첫 묶음은 실제 매장에서 운영 중인 기능이고, 나머지 둘은 트래픽과 인스턴스 증가를 가정해 별도 브랜치에서 진행한 작업이다.

### 운영 중

- **멀티테넌트 격리** — storeId를 세션에서 파생, 단건 접근은 `id+storeId`로 묶어 IDOR/BOLA 차단
- **결제 정합성** — 결제 확정을 브라우저 응답이 아닌 서버의 PG 재조회로만 처리. verify·웹훅 두 경로가 경합해도 조건부 UPDATE로 한 번만 반영
- **통신 오류와 결제 실패의 구분** — 응답을 못 받은 것을 실패로 단정할 때 생기는 이중청구를 차단
- **주문번호 채번** — (매장, 날짜) 행 비관 락으로 동시 주문 직렬화

### 동시 요청과 이벤트 전달 (별도 브랜치)

- **한정 수량 메뉴** — 조건부 UPDATE로 초과 판매 차단. 하루 100개 한정 메뉴에 300건을 넣어 100건 성공 / 200건 `OUT_OF_STOCK` 거절 확인
- **채번 락 범위 축소** — 부하 테스트로 병목이 재고가 아니라 채번 락임을 확인하고 별도 트랜잭션으로 분리. 주문 항목 1개일 때 +42%, 5개일 때 +85%
- **주문 이벤트 전달 (Outbox + Kafka)** — 발행 유실과 중복 소비를 각각 다른 층에서 차단
- **전국 매장 매출 순위 (Redis ZSET)** — 커밋 이후 갱신하고 실패분은 주기적 보정으로 복구

### 인스턴스가 여러 대일 때 (별도 브랜치)

- **스케줄러 단독 실행 (Redis 분산 락)** — 보정 배치가 인스턴스마다 중복 실행되는 문제를 락으로 제어
- **계정 단위 강제 로그아웃 (Redis 세션 외부화)** — 무상태 토큰(remember-me)이 폐기된 세션을 되살리는 경로까지 차단
- **대기 목록 실시간 전파 (SSE + Redis pub/sub)** — 폴링을 SSE로 전환하고, 알림이 처리한 인스턴스에만 가던 문제를 pub/sub으로 해결

## Package Layout

도메인 코드는 4-layer 구조로 분리. 각 레이어 안에 도메인 패키지(`menu`, `order`, `payment`, `store`)로 한 단계 더 나뉜다.

```
com.sion.pos
├── config         # SecurityConfig, WebConfig
├── interfaces     # 외부 진입점 (REST API + Thymeleaf 페이지)
├── application    # Facade (트랜잭션 경계 + 비즈니스 조율)
├── domain         # 엔티티, 도메인 서비스, Repository 인터페이스
├── infrastructure # JPA Repository 구현체, 외부 API 어댑터
└── support        # 공통 (예외, PortOne 게이트웨이, 보안 헬퍼, 시간)
```

`config` / `support` 는 어느 레이어에도 속하지 않는 횡단 관심사.

## Stack

- **Language/Runtime**: Java 21, Spring Boot 3.4.4
- **Web/Persistence**: Spring Web, Spring Data JPA, Thymeleaf, Validation
- **Security**: Spring Security (세션 form login, BCrypt)
- **DB**: MySQL 8.4 + Flyway 마이그레이션
- **PG**: PortOne v2 (카카오페이 - PC IFRAME / 모바일 REDIRECTION)
- **Test**: JUnit5, Testcontainers (MySQL)
- **Build**: Gradle Kotlin DSL
- **Infra**: Docker Compose, EC2(Amazon Linux 2023), Nginx 리버스 프록시, Let's Encrypt HTTPS
- **별도 브랜치**: Redis(세션·분산 락·ZSET), Kafka(주문 이벤트), SSE