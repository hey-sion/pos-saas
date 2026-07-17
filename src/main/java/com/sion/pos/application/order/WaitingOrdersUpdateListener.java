package com.sion.pos.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대기 목록 변경 이벤트를 커밋 이후에 받아 화면에 알린다
 */
@Component
@RequiredArgsConstructor
public class WaitingOrdersUpdateListener {

    private final WaitingOrdersNotifier notifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(WaitingOrdersUpdatedEvent event) {
        notifier.notifyUpdated(event.storeId());
    }
}