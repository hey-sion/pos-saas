package com.sion.pos.support.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class RedisWaitingOrdersSubscriberTest {

    private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
    private final RedisWaitingOrdersSubscriber subscriber = new RedisWaitingOrdersSubscriber(registry);

    @DisplayName("채널 메시지 수신 시, ")
    @Nested
    class WhenMessageReceived {

        @DisplayName("메시지의 매장 ID로 로컬 연결에 푸시한다.")
        @Test
        void pushesToLocalConnections() {
            // arrange
            Message message = mock(Message.class);
            when(message.getBody()).thenReturn("1".getBytes(StandardCharsets.UTF_8));

            // act
            subscriber.onMessage(message, null);

            // assert
            verify(registry).push(1L);
        }
    }
}