package com.sion.pos.infrastructure.order;

import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.domain.order.StoreSalesRank;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

/** 매장 매출 순위 — 날짜별 ZSET 하나에 (storeId, 매출) 을 담아 Redis 정렬을 유지 */
@Repository
@Profile("cluster")
@RequiredArgsConstructor
public class RedisSalesRankingRepository implements SalesRankingRepository {

    private static final String KEY_PREFIX = "sales:";

    // 실시간 순위용 보관 기간 — 지난 날짜는 집계 테이블에서 읽는다
    private static final Duration KEY_TTL = Duration.ofDays(3);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addAmount(LocalDate salesDate, Long storeId, int amount) {
        String key = keyOf(salesDate);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(storeId), amount);
        redisTemplate.expire(key, KEY_TTL);
    }

    @Override
    public void replaceAmount(LocalDate salesDate, Long storeId, long amount) {
        String key = keyOf(salesDate);
        redisTemplate.opsForZSet().add(key, String.valueOf(storeId), amount);
        redisTemplate.expire(key, KEY_TTL);
    }

    @Override
    public List<StoreSalesRank> findTop(LocalDate salesDate, int limit) {
        Set<TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(keyOf(salesDate), 0, limit - 1);
        if (tuples == null) {
            return List.of();
        }

        return tuples.stream()
                .map(tuple -> new StoreSalesRank(
                        Long.valueOf(tuple.getValue()),
                        tuple.getScore().longValue()))
                .toList();
    }

    @Override
    public Optional<Long> findAmount(LocalDate salesDate, Long storeId) {
        Double score = redisTemplate.opsForZSet().score(keyOf(salesDate), String.valueOf(storeId));

        return Optional.ofNullable(score).map(Double::longValue);
    }

    private String keyOf(LocalDate salesDate) {
        return KEY_PREFIX + salesDate;
    }
}