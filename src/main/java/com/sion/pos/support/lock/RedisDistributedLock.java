package com.sion.pos.support.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** 인스턴스 간 단독 실행 보장 — 만료 시간이 있어 소유자가 죽어도 락이 남지 않는다 */
@Component
@Profile("cluster")
@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {

    // 조회와 삭제 사이에 락이 만료되고 남이 잡는 경우를 막으려 한 번에 처리
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);

        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void unlock(String key, String token) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
    }
}