package com.sion.pos.application.event;

import com.sion.pos.domain.event.Outbox;

public interface OutboxDispatcher {

    void dispatch(Outbox outbox);
}