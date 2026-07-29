package com.sion.pos.support.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("cluster")
class RedisDistributedLockIntegrationTest {

    private static final String LOCK_KEY = "lock:test";
    private static final Duration TTL = Duration.ofSeconds(30);

    @Autowired private DistributedLock distributedLock;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("락을 잡을 때, ")
    class TryLock {

        @Test
        @DisplayName("비어 있으면 잡는다")
        void acquiresWhenFree() {
            // Arrange & Act
            Optional<String> token = distributedLock.tryLock(LOCK_KEY, TTL);

            // Assert
            assertThat(token).isPresent();
        }

        @Test
        @DisplayName("이미 잡혀 있으면 못 잡는다")
        void failsWhenAlreadyHeld() {
            // Arrange
            distributedLock.tryLock(LOCK_KEY, TTL);

            // Act
            Optional<String> token = distributedLock.tryLock(LOCK_KEY, TTL);

            // Assert
            assertThat(token).isEmpty();
        }

        @Test
        @DisplayName("잡은 락에는 만료 시간이 걸린다")
        void setsExpiration() {
            // Arrange & Act
            distributedLock.tryLock(LOCK_KEY, TTL);

            // Assert
            assertThat(redisTemplate.getExpire(LOCK_KEY))
                    .isPositive()
                    .isLessThanOrEqualTo(TTL.toSeconds());
        }
    }

    @Nested
    @DisplayName("락을 풀 때, ")
    class Unlock {

        @Test
        @DisplayName("푼 뒤에는 다시 잡을 수 있다")
        void allowsReacquireAfterUnlock() {
            // Arrange
            String token = distributedLock.tryLock(LOCK_KEY, TTL).orElseThrow();

            // Act
            distributedLock.unlock(LOCK_KEY, token);

            // Assert
            assertThat(distributedLock.tryLock(LOCK_KEY, TTL)).isPresent();
        }

        @Test
        @DisplayName("다른 소유자의 락은 풀지 못한다")
        void doesNotUnlockOthersLock() {
            // Arrange
            distributedLock.tryLock(LOCK_KEY, TTL);

            // Act
            distributedLock.unlock(LOCK_KEY, "someone-elses-token");

            // Assert
            assertThat(distributedLock.tryLock(LOCK_KEY, TTL)).isEmpty();
        }
    }
}