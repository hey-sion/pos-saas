package com.sion.pos.application.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.order.StoreDailySales;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import com.sion.pos.support.DatabaseCleanUp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SalesAggregationEventListenerIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private StoreDailySalesRepository storeDailySalesRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("메뉴 제공 완료 이벤트가 발행되면, ")
    class OnOrderDelivered {

        @Test
        @DisplayName("커밋 이후 그 매장의 그날 매출이 증가한다")
        void aggregatesAfterCommit() {
            // arrange
            Long storeId = 3L;
            LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 14, 3);
            OrderDeliveredEvent event = new OrderDeliveredEvent(
                    "ORDER_DELIVERED:3:2026-07-27:2", storeId, occurredAt, 9000);

            // act
            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

            // assert
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(storeId, occurredAt.toLocalDate())
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(9000L);
        }
    }
}