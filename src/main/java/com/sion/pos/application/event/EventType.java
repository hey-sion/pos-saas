package com.sion.pos.application.event;

import com.sion.pos.application.order.OrderDeliveredEvent;

public enum EventType {

    ORDER_DELIVERED(OrderDeliveredEvent.class, "order-events");

    private final Class<? extends EventPayload> payloadClass;
    private final String topic;

    EventType(Class<? extends EventPayload> payloadClass, String topic) {
        this.payloadClass = payloadClass;
        this.topic = topic;
    }

    public Class<? extends EventPayload> payloadClass() {
        return payloadClass;
    }

    public String topic() {
        return topic;
    }
}