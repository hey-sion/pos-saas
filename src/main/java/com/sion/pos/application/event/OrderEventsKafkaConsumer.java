package com.sion.pos.application.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("cluster")
@RequiredArgsConstructor
public class OrderEventsKafkaConsumer {

    private final EventSerializer eventSerializer;
    private final EventProcessor eventProcessor;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "sales-aggregation")
    public void consume(String message) {
        eventProcessor.process(eventSerializer.deserialize(message));
    }
}