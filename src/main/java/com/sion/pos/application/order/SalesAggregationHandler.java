package com.sion.pos.application.order;

import com.sion.pos.domain.event.ProcessedEventRepository;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SalesAggregationHandler {

    private final ProcessedEventRepository processedEventRepository;
    private final StoreDailySalesRepository storeDailySalesRepository;

    @Transactional
    public void applySales(OrderDeliveredEvent event) {
        int recorded = processedEventRepository.record(event.eventId());
        if (recorded == 0) {
            return;
        }

        storeDailySalesRepository.addAmount(
                event.storeId(),
                event.occurredAt().toLocalDate(),
                event.totalAmount());
    }
}