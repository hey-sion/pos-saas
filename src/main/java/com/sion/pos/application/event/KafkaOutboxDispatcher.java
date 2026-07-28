package com.sion.pos.application.event;

import com.sion.pos.domain.event.Outbox;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("cluster")
@RequiredArgsConstructor
public class KafkaOutboxDispatcher implements OutboxDispatcher {

    private final EventSerializer eventSerializer;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void dispatch(Outbox outbox) {
        Event<EventPayload> event = eventSerializer.toEvent(outbox.getEventType(), outbox.getPayload());
        send(event.type().topic(), eventSerializer.serialize(event));
    }

    private void send(String topic, String message) {
        try {
            kafkaTemplate.send(topic, message).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "Kafka 발행이 중단되었습니다.");
        } catch (Exception e) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "Kafka 발행에 실패했습니다.");
        }
    }
}