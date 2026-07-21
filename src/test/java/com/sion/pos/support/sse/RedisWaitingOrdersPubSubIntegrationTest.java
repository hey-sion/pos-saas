package com.sion.pos.support.sse;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.sion.pos.application.order.WaitingOrdersNotifier;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("cluster")
@Testcontainers
@DisplayName("Redis pub/sub 대기목록 팬아웃")
class RedisWaitingOrdersPubSubIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private WaitingOrdersNotifier notifier;

    // 이 인스턴스에 붙은 로컬 연결을 대신하는 검증 지점. Redis 를 한 바퀴 돌아 다시 로컬 push 로 도달함을 본다.
    @MockitoBean
    private SseEmitterRegistry registry;

    @DisplayName("한 인스턴스가 발행한 변경 신호가 Redis 를 거쳐 구독 인스턴스의 로컬 푸시로 도달한다.")
    @Test
    void deliversAcrossInstancesViaRedis() {
        // act & assert — 구독이 붙기 전 발행분은 유실되므로(pub/sub), 도달할 때까지 반복 발행한다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            notifier.notifyUpdated(1L);
            verify(registry, atLeastOnce()).push(1L);
        });
    }
}