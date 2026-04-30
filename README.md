# POS SaaS

소상공인 매장용 웹 POS 시스템과 QR 간편결제를 위한 Spring Boot 프로젝트입니다.

## Stack

- Java 21
- Spring Boot 3.4.4
- Gradle Kotlin DSL
- Spring Web, Validation, Data JPA, Thymeleaf
- MySQL
- Lombok
- Testcontainers

## Local Development

Run only local infrastructure with Docker, then start the Spring Boot app from your IDE or Gradle.

```shell
docker compose -f docker/infra-compose.yml up -d
```

```shell
./gradlew bootRun
```

## Docker Deployment Check

Run both the Spring Boot app and MySQL in Docker. This is the closest local check before EC2 deployment.

```shell
docker compose -f docker/app-compose.yml up -d --build
```

## Initial Package Layout

```text
com.sion.pos
├── store
├── menu
├── order
├── payment
├── sales
└── global
```
