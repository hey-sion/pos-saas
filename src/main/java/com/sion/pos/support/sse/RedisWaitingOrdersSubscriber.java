package com.sion.pos.support.sse;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** Redis 채널로 들어온 변경 신호를 이 인스턴스에 붙은 연결로 push */
@Component
@Profile("cluster")
@RequiredArgsConstructor
public class RedisWaitingOrdersSubscriber implements MessageListener {

    private final SseEmitterRegistry registry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Long storeId = Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8));
        registry.push(storeId);
    }
}