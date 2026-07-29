package com.sion.pos.application.order;

import com.sion.pos.domain.event.ProcessedEventRepository;
import com.sion.pos.domain.order.SalesRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@Profile("cluster")
@RequiredArgsConstructor
public class SalesRankingUpdater {

    private static final String CONSUMER = "sales-ranking";

    private final ProcessedEventRepository processedEventRepository;
    private final SalesRankingRepository salesRankingRepository;

    @Transactional
    public void applyRanking(OrderDeliveredEvent event) {
        int recorded = processedEventRepository.record(CONSUMER, event.eventId());
        if (recorded == 0) {
            return;
        }

        // Redis 는 롤백되지 않으므로 커밋 이후에 갱신
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateRanking(event);
            }
        });
    }

    private void updateRanking(OrderDeliveredEvent event) {
        try {
            salesRankingRepository.addAmount(event.orderDate(), event.storeId(), event.totalAmount());
        } catch (Exception e) {
            // 보정 배치가 집계 테이블 값으로 덮어쓰므로 여기서 실패해도 다음 주기에 복구
            log.warn("매출 순위 갱신 실패, eventId={}", event.eventId(), e);
        }
    }
}