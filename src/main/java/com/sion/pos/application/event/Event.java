package com.sion.pos.application.event;

public record Event<T extends EventPayload>(String eventId, EventType type, T payload) {

    public static <T extends EventPayload> Event<T> of(String eventId, EventType type, T payload) {
        return new Event<>(eventId, type, payload);
    }
}