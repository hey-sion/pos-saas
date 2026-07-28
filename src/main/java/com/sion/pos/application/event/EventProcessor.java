package com.sion.pos.application.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventProcessor {

    private final List<EventHandler> eventHandlers;

    public void process(Event<? extends EventPayload> event) {
        for (EventHandler handler : eventHandlers) {
            if (handler.supports(event.type())) {
                handler.handle(event);
            }
        }
    }
}