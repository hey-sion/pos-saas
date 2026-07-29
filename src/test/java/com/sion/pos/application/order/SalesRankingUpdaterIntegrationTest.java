package com.sion.pos.application.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.support.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("cluster")
class SalesRankingUpdaterIntegrationTest {

    private static final Long STORE_ID = 3L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 7, 27);

    @Autowired private SalesRankingUpdater salesRankingUpdater;
    @Autowired private DailySalesUpdater dailySalesUpdater;
    @Autowired private SalesRankingRepository salesRankingRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderDeliveredEvent eventOf(String eventId, int totalAmount) {
        return new OrderDeliveredEvent(eventId, STORE_ID, ORDER_DATE, ORDER_DATE.atTime(14, 3), totalAmount);
    }

    @Nested
    @DisplayName("메뉴 제공 완료 이벤트로 순위를 갱신할 때, ")
    class ApplyRanking {

        @Test
        @DisplayName("그 매장의 그날 매출이 순위에 반영된다")
        void addsSalesToRanking() {
            // Arrange & Act
            salesRankingUpdater.applyRanking(eventOf("evt-1", 9000));

            // Assert
            assertThat(salesRankingRepository.findAmount(ORDER_DATE, STORE_ID)).contains(9000L);
        }

        @Test
        @DisplayName("같은 이벤트를 두 번 처리해도 한 번만 반영된다")
        void ignoresDuplicateEvent() {
            // Arrange
            OrderDeliveredEvent event = eventOf("evt-1", 9000);

            // Act
            salesRankingUpdater.applyRanking(event);
            salesRankingUpdater.applyRanking(event);

            // Assert
            assertThat(salesRankingRepository.findAmount(ORDER_DATE, STORE_ID)).contains(9000L);
        }

        @Test
        @DisplayName("매출 집계가 이미 처리한 이벤트라도 순위에는 따로 반영된다")
        void isIndependentOfOtherConsumer() {
            // Arrange
            OrderDeliveredEvent event = eventOf("evt-1", 9000);
            dailySalesUpdater.applySales(event);

            // Act
            salesRankingUpdater.applyRanking(event);

            // Assert
            assertThat(salesRankingRepository.findAmount(ORDER_DATE, STORE_ID)).contains(9000L);
        }
    }
}