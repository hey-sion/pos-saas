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

@SpringBootTest
class SalesAggregationHandlerIntegrationTest {

    @Autowired private SalesAggregationHandler salesAggregationHandler;
    @Autowired private StoreDailySalesRepository storeDailySalesRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("메뉴 제공 완료 이벤트를 처리할 때, ")
    class ApplySales {

        @Test
        @DisplayName("그 매장의 그날 매출과 주문 수가 증가한다")
        void increasesDailySales() {
            // arrange
            Long storeId = 3L;
            LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 14, 3);
            OrderDeliveredEvent event = new OrderDeliveredEvent("evt-1", storeId, occurredAt, 9000);

            // act
            salesAggregationHandler.applySales(event);

            // assert
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(storeId, occurredAt.toLocalDate())
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(9000L);
            assertThat(sales.getOrderCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("여러 주문이 제공완료되면 그날 매출이 합산된다")
        void accumulatesMultipleOrders() {
            // arrange
            Long storeId = 3L;
            LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 14, 3);

            // act
            salesAggregationHandler.applySales(new OrderDeliveredEvent("evt-1", storeId, occurredAt, 9000));
            salesAggregationHandler.applySales(new OrderDeliveredEvent("evt-2", storeId, occurredAt, 5000));

            // assert
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(storeId, occurredAt.toLocalDate())
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(14000L);
            assertThat(sales.getOrderCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 이벤트를 두 번 처리해도 매출은 한 번만 반영된다")
        void ignoresDuplicateEvent() {
            // arrange
            Long storeId = 3L;
            LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 14, 3);
            OrderDeliveredEvent event = new OrderDeliveredEvent("evt-1", storeId, occurredAt, 9000);

            // act
            salesAggregationHandler.applySales(event);
            salesAggregationHandler.applySales(event);

            // assert
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(storeId, occurredAt.toLocalDate())
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(9000L);
            assertThat(sales.getOrderCount()).isEqualTo(1);
        }
    }
}