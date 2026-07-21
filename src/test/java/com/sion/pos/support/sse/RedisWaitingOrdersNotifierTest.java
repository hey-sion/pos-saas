package com.sion.pos.support.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisWaitingOrdersNotifierTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisWaitingOrdersNotifier notifier = new RedisWaitingOrdersNotifier(redisTemplate);

    @DisplayName("대기목록 변경 알림 시, ")
    @Nested
    class WhenNotifying {

        @DisplayName("매장 ID를 Redis 채널에 발행한다.")
        @Test
        void publishesStoreIdToChannel() {
            // act
            notifier.notifyUpdated(1L);

            // assert
            verify(redisTemplate).convertAndSend(RedisWaitingOrdersNotifier.CHANNEL, "1");
        }
    }
}