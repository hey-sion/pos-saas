# POS SaaS

소상공인 매장에서 쓰는 웹 POS와 손님용 QR 셀프 주문 기능을 함께 제공한다. 한 매장에만 묶이지 않고 다른 매장에서도 같은 코드를 그대로 쓸 수 있도록 매장 단위로 데이터가 분리되는 멀티테넌트 구조로 설계했다.

사장님은 태블릿/PC 웹에서 메뉴를 관리하고 매장에서 주문을 받는다. 손님은 매장에 붙은 QR을 찍으면 모바일에서 메뉴를 보고 직접 주문·결제까지 끝낼 수 있다. 결제는 두 갈래로 나뉜다. 사장님이 POS에서 직접 처리하는 오프라인 결제(현금, 실물 카드)와, 손님이 셀프로 처리하는 간편결제(카카오페이/PortOne 연동).

> 설계 배경·핵심 의사결정·트레이드오프 등은 별도의 문서([case-study](docs/portfolio/case-study.md))에 정리

## Stack

- **Language/Runtime**: Java 21, Spring Boot 3.4.4
- **Web/Persistence**: Spring Web, Spring Data JPA, Thymeleaf, Validation
- **Security**: Spring Security (세션 form login, BCrypt)
- **DB**: MySQL 8.4 + Flyway 마이그레이션
- **PG**: PortOne v2 (카카오페이 - PC IFRAME / 모바일 REDIRECTION)
- **Test**: JUnit5, Testcontainers (MySQL)
- **Build**: Gradle Kotlin DSL
- **Infra**: Docker Compose, EC2(Amazon Linux 2023), Nginx 리버스 프록시, Let's Encrypt HTTPS

## Screens

| 매장 POS (메뉴 선택·주문 접수·고객 대기) | 손님 QR 셀프 주문 (모바일) |
|:---:|:---:|
| <img src="docs/images/pos-main.png" width="520" alt="매장 POS 화면" /> | <img src="docs/images/customer-self-order.png" width="200" alt="손님 QR 셀프 주문 화면" /> |

## Production

현재 운영 중:
- 매장 POS 페이지: `https://smilepos.kr` (로그인 필요)
- 손님 셀프 주문 페이지: `https://smilepos.kr/order/cafe-spring` (모바일용 샘플 페이지, 테스트 결제 가능)

EC2 단일 인스턴스 + Docker Compose + Nginx 리버스 프록시 + Let's Encrypt HTTPS 조합.

- DNS: 가비아 도메인 + Route53 호스팅 영역 + A 레코드 (EIP 고정)
- 컨테이너: `restart: unless-stopped` 로 인스턴스 재시작 시 자동 복구
- 인증서: certbot 자동 갱신 (`certbot-renew.timer`)
- 배포: 현재 수동. CI/CD는 도입 예정 (다음 단계)

## Architecture Highlights

### 1. 멀티테넌트 격리

한 매장이 다른 매장의 메뉴/주문/결제를 절대 볼 수 없어야 한다.

- 어떤 매장의 요청인지는 로그인 세션에서 서버가 결정한다. 클라이언트가 보낸 값으로 매장을 판단하지 않는다
- 단건 조회/수정/삭제 모두 매장 ID와 함께 묶어서 처리한다. 목록 필터링만 걸어두면 자원 ID만 추측해도 다른 매장 데이터에 접근할 수 있다

### 2. 결제 정합성 — verify + 웹훅 이중화

PG 결과는 클라이언트가 받은 응답을 그대로 믿지 않는다. 결제 상태는 항상 서버가 PortOne API를 호출해 확정한 값을 기준으로 한다.

- 손님 셀프결제: 브라우저 → 서버 → PortOne 재조회 → DB UPDATE
- 같은 결제 건에 대해 PortOne 웹훅도 별도로 들어오기 때문에 verify 흐름과 동시에 처리되는 경합이 발생할 수 있다
- 결제 상태 전이는 "현재 PENDING일 때만 COMPLETED로 바꾼다" 같은 조건부 UPDATE로 처리한다. 두 경로가 동시에 들어와도 DB가 한쪽만 성공시켜 후속 처리는 한 번만 일어난다
- 웹훅은 서명 검증 후에도 페이로드를 신뢰하지 않고 PortOne 재조회로 다시 확인한다

### 3. 주문번호 채번 — 매장·일자별 비관 락

매장마다 매일 1번부터 주문번호를 새로 매긴다. 단순 `MAX+1`은 동시 주문에서 같은 번호가 나올 수 있어 직렬화가 필요하다.

- 별도 채번 테이블을 두고 (매장, 날짜) 단위로 행을 관리한다. 해당 로우에 비관 락을 걸어 직렬화 — 행 단위 락 범위로 매장·일자 간 경합 방지
- 동시 주문 시나리오에서 같은 번호가 나오지 않는지 회귀 테스트로 검증한다

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

## References

- 케이스 스터디 (설계·의사결정): [`docs/portfolio/case-study.md`](docs/portfolio/case-study.md)
- 컨벤션 / AI 협업 가이드: [`CLAUDE.md`](CLAUDE.md)
