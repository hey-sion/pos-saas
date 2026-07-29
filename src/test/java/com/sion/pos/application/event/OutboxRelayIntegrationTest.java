package com.sion.pos.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.domain.event.Outbox;
import com.sion.pos.domain.event.OutboxRepository;
import com.sion.pos.domain.order.StoreDailySales;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import com.sion.pos.support.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OutboxRelayIntegrationTest {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private OutboxEventPublisher outboxEventPublisher;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private StoreDailySalesRepository storeDailySalesRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("대기 중인 outbox를 전달할 때, ")
    class RelayPending {

        @Test
        @DisplayName("소비자가 처리해 매출이 집계되고 해당 행은 발행 처리된다")
        void processesAndMarksPublished() {
            // arrange
            LocalDate orderDate = LocalDate.of(2026, 7, 27);
            outboxEventPublisher.publish("ORDER_DELIVERED",
                    new OrderDeliveredEvent("ORDER_DELIVERED:3:2026-07-27:2", 3L,
                            orderDate, orderDate.atTime(14, 3), 9000));

            // act
            outboxRelay.relayPending();

            // assert
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(3L, orderDate)
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(9000L);
            Outbox row = outboxRepository.findAll().get(0);
            assertThat(row.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 발행된 행은 다시 처리하지 않아 매출이 한 번만 집계된다")
        void doesNotReprocessPublished() {
            // arrange
            LocalDate orderDate = LocalDate.of(2026, 7, 27);
            outboxEventPublisher.publish("ORDER_DELIVERED",
                    new OrderDeliveredEvent("ORDER_DELIVERED:3:2026-07-27:2", 3L,
                            orderDate, orderDate.atTime(14, 3), 9000));

            // act
            outboxRelay.relayPending();
            outboxRelay.relayPending();

            // assert
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(3L, orderDate)
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(9000L);
        }
    }
}