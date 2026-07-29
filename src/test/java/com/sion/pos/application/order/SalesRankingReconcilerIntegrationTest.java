package com.sion.pos.application.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.lock.DistributedLock;
import com.sion.pos.support.time.BusinessTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("cluster")
class SalesRankingReconcilerIntegrationTest {

    private static final LocalDate SALES_DATE = LocalDate.of(2026, 7, 27);

    @Autowired private SalesRankingReconciler salesRankingReconciler;
    @Autowired private SalesRankingRepository salesRankingRepository;
    @Autowired private StoreDailySalesRepository storeDailySalesRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void givenAggregatedSales(Long storeId, int amount) {
        givenAggregatedSalesOn(SALES_DATE, storeId, amount);
    }

    private void givenAggregatedSalesOn(LocalDate salesDate, Long storeId, int amount) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> storeDailySalesRepository.addAmount(storeId, salesDate, amount));
    }

    @Nested
    @DisplayName("매출 순위를 보정할 때, ")
    class Reconcile {

        @Test
        @DisplayName("집계 테이블 값으로 순위가 채워진다")
        void fillsRankingFromAggregatedSales() {
            // Arrange
            givenAggregatedSales(1L, 9000);
            givenAggregatedSales(2L, 12000);
            LocalDateTime beforeSalesRecorded = LocalDateTime.now(BusinessTime.ZONE).minusHours(1);

            // Act
            salesRankingReconciler.reconcileChangedSince(beforeSalesRecorded);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).contains(9000L);
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 2L)).contains(12000L);
        }

        @Test
        @DisplayName("순위 값이 틀어져 있어도 집계 테이블 값으로 맞춰진다")
        void overwritesDriftedRanking() {
            // Arrange
            givenAggregatedSales(1L, 9000);
            salesRankingRepository.addAmount(SALES_DATE, 1L, 99000);
            LocalDateTime beforeSalesRecorded = LocalDateTime.now(BusinessTime.ZONE).minusHours(1);

            // Act
            salesRankingReconciler.reconcileChangedSince(beforeSalesRecorded);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).contains(9000L);
        }

        @Test
        @DisplayName("바뀐 지 오래된 매출은 건드리지 않는다")
        void ignoresUnchangedSales() {
            // Arrange
            givenAggregatedSales(1L, 9000);
            LocalDateTime afterSalesRecorded = LocalDateTime.now(BusinessTime.ZONE).plusMinutes(1);

            // Act
            salesRankingReconciler.reconcileChangedSince(afterSalesRecorded);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).isEmpty();
        }

        @Test
        @DisplayName("지난 날짜 매출이라도 최근에 바뀌었으면 그 날짜 순위로 보정한다")
        void reconcilesPastDateWhenRecentlyChanged() {
            // Arrange — 자정을 넘겨 제공한 주문이 어제 매출로 잡히는 상황
            LocalDate pastDate = BusinessTime.today().minusDays(1);
            givenAggregatedSalesOn(pastDate, 1L, 9000);

            // Act
            salesRankingReconciler.reconcileRecentlyChanged();

            // Assert
            assertThat(salesRankingRepository.findAmount(pastDate, 1L)).contains(9000L);
        }

        @Test
        @DisplayName("다른 인스턴스가 보정 중이면 건너뛴다")
        void skipsWhenAnotherInstanceHoldsLock() {
            // Arrange
            givenAggregatedSales(1L, 9000);
            distributedLock.tryLock(SalesRankingReconciler.LOCK_KEY, Duration.ofMinutes(1));

            // Act
            salesRankingReconciler.reconcileRecentlyChanged();

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).isEmpty();
        }

        @Test
        @DisplayName("여러 번 보정해도 결과가 같다")
        void isIdempotent() {
            // Arrange
            givenAggregatedSales(1L, 9000);
            LocalDateTime beforeSalesRecorded = LocalDateTime.now(BusinessTime.ZONE).minusHours(1);

            // Act
            salesRankingReconciler.reconcileChangedSince(beforeSalesRecorded);
            salesRankingReconciler.reconcileChangedSince(beforeSalesRecorded);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).contains(9000L);
        }
    }
}