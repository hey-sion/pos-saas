package com.sion.pos.application.event;

import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.application.order.SalesAggregationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderDeliveredEventHandler implements EventHandler {

    private final SalesAggregationHandler salesAggregationHandler;

    @Override
    public boolean supports(EventType type) {
        return type == EventType.ORDER_DELIVERED;
    }

    @Override
    public void handle(Event<? extends EventPayload> event) {
        salesAggregationHandler.applySales((OrderDeliveredEvent) event.payload());
    }
}