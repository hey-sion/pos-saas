package com.sion.pos.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cluster")
@Testcontainers
@DisplayName("모든 기기 로그아웃 API")
class AuthSessionApiE2ETest {

    private static final String PASSWORD = "password1!";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--notify-keyspace-events", "Egx");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreAccountService storeAccountService;
    @Autowired private RedisIndexedSessionRepository sessionRepository;
    @Autowired private RedisConnectionFactory redisConnectionFactory;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        // 정적 Redis 컨테이너를 테스트 간 공유하므로 세션도 매 테스트 후 비운다(격리).
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Nested
    @DisplayName("한 계정이 여러 기기에서 로그인해 있을 때, ")
    class WhenLoggedInOnMultipleDevices {

        @Test
        @DisplayName("모든 기기 로그아웃을 요청하면 그 계정의 세션이 전부 사라진다.")
        void logsOutAllDevices() {
            // Arrange — owner 계정으로 기기 2대에서 로그인
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);
            TestRestTemplate device1 = ApiTestClient.loggedIn(port, "owner", PASSWORD);
            ApiTestClient.loggedIn(port, "owner", PASSWORD);
            assertThat(sessionRepository.findByPrincipalName("owner")).hasSize(2);

            // Act — 한 기기에서 "모든 기기 로그아웃" 호출
            ResponseEntity<Void> response =
                    device1.postForEntity("/api/v1/auth/logout-all-devices", null, Void.class);

            // Assert — 204 + 그 계정 세션 전부 무효화
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(sessionRepository.findByPrincipalName("owner")).isEmpty();
        }

        @Test
        @DisplayName("로그아웃 후 다른 기기는 인증이 풀린다 — remember-me 가 재인증시키지 않는다.")
        void otherDeviceIsNotRescuedByRememberMe() {
            // Arrange — owner 계정으로 기기 2대 로그인
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);
            TestRestTemplate device1 = ApiTestClient.loggedIn(port, "owner", PASSWORD);
            TestRestTemplate device2 = ApiTestClient.loggedIn(port, "owner", PASSWORD);
            assertThat(device2.getForEntity("/api/v1/orders/waiting", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            // Act — device1 에서 모든 기기 로그아웃
            device1.postForEntity("/api/v1/auth/logout-all-devices", null, Void.class);

            // Assert — device2 는 인증이 풀려야 한다(remember-me 로 되살아나면 안 됨)
            assertThat(device2.getForEntity("/api/v1/orders/waiting", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}