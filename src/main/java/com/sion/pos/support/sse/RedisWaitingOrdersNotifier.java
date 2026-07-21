package com.sion.pos.support.sse;

import com.sion.pos.application.order.WaitingOrdersNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** cluster 프로파일용 대기목록 알림 — 변경 신호를 Redis 채널에 발행 */
@Component
@Profile("cluster")
@RequiredArgsConstructor
public class RedisWaitingOrdersNotifier implements WaitingOrdersNotifier {

    public static final String CHANNEL = "waiting-orders";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void notifyUpdated(Long storeId) {
        redisTemplate.convertAndSend(CHANNEL, String.valueOf(storeId));
    }
}