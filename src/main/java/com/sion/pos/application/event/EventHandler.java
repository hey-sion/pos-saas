package com.sion.pos.application.event;

public interface EventHandler {

    boolean supports(EventType type);

    void handle(Event<? extends EventPayload> event);
}