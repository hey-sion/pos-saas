package com.sion.pos.application.event;

import com.sion.pos.domain.event.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!cluster")
@RequiredArgsConstructor
public class LocalOutboxDispatcher implements OutboxDispatcher {

    private final EventSerializer eventSerializer;
    private final EventProcessor eventProcessor;

    @Override
    public void dispatch(Outbox outbox) {
        eventProcessor.process(eventSerializer.toEvent(outbox.getEventType(), outbox.getPayload()));
    }
}