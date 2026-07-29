package com.sion.pos.application.event;

import com.sion.pos.application.order.DailySalesUpdater;
import com.sion.pos.application.order.OrderDeliveredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesAggregationEventHandler implements EventHandler {

    private final DailySalesUpdater dailySalesUpdater;

    @Override
    public boolean supports(EventType type) {
        return type == EventType.ORDER_DELIVERED;
    }

    @Override
    public void handle(Event<? extends EventPayload> event) {
        dailySalesUpdater.applySales((OrderDeliveredEvent) event.payload());
    }
}