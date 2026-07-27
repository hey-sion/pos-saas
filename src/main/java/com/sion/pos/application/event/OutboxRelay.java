package com.sion.pos.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.application.order.SalesAggregationHandler;
import com.sion.pos.domain.event.Outbox;
import com.sion.pos.domain.event.OutboxRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.List;
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
    private final SalesAggregationHandler salesAggregationHandler;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000)
    public void relayPending() {
        List<Outbox> pending = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, BATCH_SIZE));
        for (Outbox outbox : pending) {
            try {
                dispatch(outbox);
                outbox.markPublished();
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("outbox 전달 실패, id={}", outbox.getId(), e);
                // published_at 그대로 → 다음 폴링에서 재시도
            }
        }
    }

    private void dispatch(Outbox outbox) {
        if (!"ORDER_DELIVERED".equals(outbox.getEventType())) {
            log.warn("처리기 없는 이벤트 타입, 건너뜀: {}", outbox.getEventType());
            return;
        }

        salesAggregationHandler.applySales(deserialize(outbox.getPayload()));
    }

    private OrderDeliveredEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, OrderDeliveredEvent.class);
        } catch (JsonProcessingException e) {
            throw new PosApplicationException(ErrorType.INTERNAL_ERROR, "이벤트 역직렬화에 실패했습니다.");
        }
    }
}