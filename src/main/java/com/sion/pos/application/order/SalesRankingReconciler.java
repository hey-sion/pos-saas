package com.sion.pos.application.order;

import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.domain.order.StoreDailySales;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import com.sion.pos.support.lock.DistributedLock;
import com.sion.pos.support.time.BusinessTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Redis 장애로 벌어진 순위와 집계 테이블의 차이를 주기적으로 메운다 */
@Slf4j
@Component
@Profile("cluster")
@RequiredArgsConstructor
public class SalesRankingReconciler {

    public static final String LOCK_KEY = "lock:sales-ranking-reconcile";

    private static final long INTERVAL_MS = 300_000;
    private static final Duration LOCK_TTL = Duration.ofMinutes(4);
    // 배치가 몇 번 걸러도 메우도록 실행 주기보다 넉넉히 잡는다
    private static final Duration LOOKBACK = Duration.ofMinutes(30);

    private final StoreDailySalesRepository storeDailySalesRepository;
    private final SalesRankingRepository salesRankingRepository;
    private final DistributedLock distributedLock;

    /** 인스턴스마다 타이머가 도므로 락을 잡은 한 대만 실제로 보정한다 */
    @Scheduled(fixedDelay = INTERVAL_MS)
    public void reconcileRecentlyChanged() {
        Optional<String> token = distributedLock.tryLock(LOCK_KEY, LOCK_TTL);
        if (token.isEmpty()) {
            return;
        }

        try {
            reconcileChangedSince(LocalDateTime.now(BusinessTime.ZONE).minus(LOOKBACK));
        } catch (Exception e) {
            log.warn("매출 순위 보정 실패", e);
        } finally {
            distributedLock.unlock(LOCK_KEY, token.get());
        }
    }

    /** 행마다 자기 날짜를 들고 있어 보정하는 쪽은 날짜를 몰라도 된다 — 자정 경계 예외 없음 */
    public void reconcileChangedSince(LocalDateTime since) {
        long startedAt = System.currentTimeMillis();

        List<StoreDailySales> changed = storeDailySalesRepository.findByUpdatedAtAfter(since);
        for (StoreDailySales sales : changed) {
            salesRankingRepository.replaceAmount(
                    sales.getSalesDate(), sales.getStoreId(), sales.getSalesAmount());
        }

        log.info("매출 순위 보정 {}건, {}ms", changed.size(), System.currentTimeMillis() - startedAt);
    }
}