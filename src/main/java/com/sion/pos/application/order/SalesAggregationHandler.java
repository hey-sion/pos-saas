package com.sion.pos.application.order;

import com.sion.pos.domain.event.ProcessedEventRepository;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SalesAggregationHandler {

    private static final String CONSUMER = "sales-aggregation";

    private final ProcessedEventRepository processedEventRepository;
    private final StoreDailySalesRepository storeDailySalesRepository;

    @Transactional
    public void applySales(OrderDeliveredEvent event) {
        int recorded = processedEventRepository.record(CONSUMER, event.eventId());
        if (recorded == 0) {
            return;
        }

        // 자정을 넘겨 제공해도 주문일 기준 매출로 집계
        storeDailySalesRepository.addAmount(
                event.storeId(),
                event.orderDate(),
                event.totalAmount());
    }
}