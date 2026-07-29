package com.sion.pos.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.domain.event.Outbox;
import com.sion.pos.domain.event.OutboxRepository;
import com.sion.pos.support.DatabaseCleanUp;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OutboxEventPublisherIntegrationTest {

    @Autowired private OutboxEventPublisher outboxEventPublisher;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("이벤트를 발행하면, ")
    class Publish {

        @Test
        @DisplayName("아직 전달되지 않은(published_at=null) outbox 행으로 저장된다")
        void savesPendingOutboxRow() {
            // arrange
            OrderDeliveredEvent event = new OrderDeliveredEvent(
                    "ORDER_DELIVERED:3:2026-07-27:2", 3L,
                    LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 27).atTime(14, 3), 9000);

            // act
            outboxEventPublisher.publish("ORDER_DELIVERED", event);

            // assert
            List<Outbox> rows = outboxRepository.findAll();
            assertThat(rows).hasSize(1);
            Outbox row = rows.get(0);
            assertThat(row.getEventType()).isEqualTo("ORDER_DELIVERED");
            assertThat(row.getPublishedAt()).isNull();
            assertThat(row.getPayload()).contains("ORDER_DELIVERED:3:2026-07-27:2");
        }
    }
}