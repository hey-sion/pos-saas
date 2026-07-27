package com.sion.pos.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SalesAggregationEventListener {

    private final SalesAggregationHandler salesAggregationHandler;

    // 앞선 트랜잭션은 커밋 완료 상태 → REQUIRES_NEW로 새 트랜잭션에서 집계 저장 (배경: docs/learnings/0019)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderDeliveredEvent event) {
        salesAggregationHandler.applySales(event);
    }
}