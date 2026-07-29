package com.sion.pos.application.event;

import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.application.order.SalesRankingUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("cluster")
@RequiredArgsConstructor
public class SalesRankingEventHandler implements EventHandler {

    private final SalesRankingUpdater salesRankingUpdater;

    @Override
    public boolean supports(EventType type) {
        return type == EventType.ORDER_DELIVERED;
    }

    @Override
    public void handle(Event<? extends EventPayload> event) {
        salesRankingUpdater.applyRanking((OrderDeliveredEvent) event.payload());
    }
}