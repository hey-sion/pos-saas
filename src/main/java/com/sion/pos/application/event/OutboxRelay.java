package com.sion.pos.application.event;

import com.sion.pos.domain.event.Outbox;
import com.sion.pos.domain.event.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final OutboxDispatcher outboxDispatcher;

    @Scheduled(fixedDelay = 2000)
    public void relayPending() {
        for (Outbox outbox : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, BATCH_SIZE))) {
            try {
                outboxDispatcher.dispatch(outbox);
                outbox.markPublished();
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("outbox 전달 실패, id={}", outbox.getId(), e);
                // published_at 그대로 → 다음 폴링에서 재시도
            }
        }
    }
}