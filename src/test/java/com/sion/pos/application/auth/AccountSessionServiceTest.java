package com.sion.pos.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("cluster")
@Testcontainers
@DisplayName("AccountSessionService — 계정 세션 무효화")
class AccountSessionServiceTest {

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

    @Autowired
    private AccountSessionService accountSessionService;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @AfterEach
    void tearDown() {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Nested
    @DisplayName("한 계정이 여러 기기에서 로그인해 있을 때, ")
    class WhenLoggedInOnMultipleDevices {

        @Test
        @DisplayName("모든 기기 로그아웃 시 그 계정의 세션을 전부 무효화한다.")
        void invalidatesAllSessionsOfAccount() {
            // Arrange — cafe-spring 계정으로 로그인된 세션 2개(기기 2대)
            String loginId = "cafe-spring";
            createAuthenticatedSession(loginId);
            createAuthenticatedSession(loginId);
            assertThat(findSessions(loginId)).hasSize(2);

            // Act — 모든 기기 로그아웃
            int invalidated = accountSessionService.logoutAllDevices(loginId);

            // Assert — 두 세션 모두 사라진다
            assertThat(invalidated).isEqualTo(2);
            assertThat(findSessions(loginId)).isEmpty();
        }

        @Test
        @DisplayName("다른 계정의 세션은 건드리지 않는다.")
        void doesNotTouchOtherAccountsSessions() {
            // Arrange — cafe-spring 1대, smile-guest 1대
            createAuthenticatedSession("cafe-spring");
            createAuthenticatedSession("smile-guest");

            // Act — cafe-spring 만 로그아웃
            accountSessionService.logoutAllDevices("cafe-spring");

            // Assert — smile-guest 세션은 살아있다
            assertThat(findSessions("cafe-spring")).isEmpty();
            assertThat(findSessions("smile-guest")).hasSize(1);
        }
    }

    private void createAuthenticatedSession(String loginId) {
        var session = sessionRepository.createSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(loginId, "n/a", List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        sessionRepository.save(session);
    }

    private java.util.Map<String, ?> findSessions(String loginId) {
        return sessionRepository.findByPrincipalName(loginId);
    }
}