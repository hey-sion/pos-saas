package com.sion.pos.application.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.support.DatabaseCleanUp;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class OrderNumberIssuerIntegrationTest {

    @Autowired private OrderNumberIssuer orderNumberIssuer;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("주문번호 발급 시, ")
    class Issue {

        @Test
        @DisplayName("시퀀스 행이 없으면 첫 주문번호로 1을 발급한다")
        void issuesOneWhenSequenceDoesNotExist() {
            // arrange
            Long storeId = 1L;
            LocalDate orderDate = LocalDate.of(2026, 5, 8);

            // act
            int orderNumber = issue(storeId, orderDate);

            // assert
            assertThat(orderNumber).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 매장/날짜의 주문번호를 순차 증가시킨다")
        void incrementsOrderNumberForSameStoreAndDate() {
            // arrange
            Long storeId = 1L;
            LocalDate orderDate = LocalDate.of(2026, 5, 8);

            // act
            int first = issue(storeId, orderDate);
            int second = issue(storeId, orderDate);
            int third = issue(storeId, orderDate);

            // assert
            assertThat(List.of(first, second, third)).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("다른 매장/날짜의 주문번호는 서로 독립적으로 발급한다")
        void issuesOrderNumbersIndependentlyByStoreAndDate() {
            // arrange
            Long storeId = 1L;
            Long otherStoreId = 2L;
            LocalDate orderDate = LocalDate.of(2026, 5, 8);
            LocalDate otherDate = orderDate.plusDays(1);

            // act
            int first = issue(storeId, orderDate);
            int otherStoreFirst = issue(otherStoreId, orderDate);
            int otherDateFirst = issue(storeId, otherDate);
            int second = issue(storeId, orderDate);

            // assert
            assertThat(first).isEqualTo(1);
            assertThat(otherStoreFirst).isEqualTo(1);
            assertThat(otherDateFirst).isEqualTo(1);
            assertThat(second).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 매장/날짜에 동시에 발급해도 중복 없이 연속된 주문번호를 발급한다")
        void issuesSequentialOrderNumbersConcurrently() {
            // arrange
            Long storeId = 1L;
            LocalDate orderDate = LocalDate.of(2026, 5, 8);
            int requestCount = 20;
            ExecutorService executorService = Executors.newFixedThreadPool(8);
            CountDownLatch startLatch = new CountDownLatch(1);

            try {
                // act
                List<CompletableFuture<Integer>> futures = IntStream.range(0, requestCount)
                        .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                            await(startLatch);
                            return issue(storeId, orderDate);
                        }, executorService))
                        .toList();
                startLatch.countDown();
                List<Integer> issuedNumbers = futures.stream()
                        .map(CompletableFuture::join)
                        .toList();

                // assert
                assertThat(issuedNumbers).containsExactlyInAnyOrderElementsOf(
                        IntStream.rangeClosed(1, requestCount).boxed().toList());
            } finally {
                executorService.shutdown();
            }
        }
    }

    private int issue(Long storeId, LocalDate orderDate) {
        return transactionTemplate.execute(status -> orderNumberIssuer.issue(storeId, orderDate));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}