package com.sion.pos.infrastructure.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.domain.order.StoreSalesRank;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
class RedisSalesRankingRepositoryIntegrationTest {

    private static final LocalDate SALES_DATE = LocalDate.of(2026, 7, 27);

    @Autowired private SalesRankingRepository salesRankingRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("매출을 더할 때, ")
    class AddAmount {

        @Test
        @DisplayName("같은 매장의 매출이 누적된다")
        void accumulatesAmount() {
            // Arrange & Act
            salesRankingRepository.addAmount(SALES_DATE, 1L, 9000);
            salesRankingRepository.addAmount(SALES_DATE, 1L, 5000);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).contains(14000L);
        }

        @Test
        @DisplayName("날짜 키에 만료 시간이 걸린다")
        void setsKeyExpiration() {
            // Arrange & Act
            salesRankingRepository.addAmount(SALES_DATE, 1L, 9000);

            // Assert
            Long ttlSeconds = redisTemplate.getExpire("sales:" + SALES_DATE);
            assertThat(ttlSeconds)
                    .isPositive()
                    .isLessThanOrEqualTo(Duration.ofDays(3).toSeconds());
        }

        @Test
        @DisplayName("날짜가 다르면 따로 집계된다")
        void separatesByDate() {
            // Arrange & Act
            salesRankingRepository.addAmount(SALES_DATE, 1L, 9000);
            salesRankingRepository.addAmount(SALES_DATE.plusDays(1), 1L, 5000);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).contains(9000L);
            assertThat(salesRankingRepository.findAmount(SALES_DATE.plusDays(1), 1L)).contains(5000L);
        }
    }

    @Nested
    @DisplayName("상위 매장을 조회할 때, ")
    class FindTop {

        @Test
        @DisplayName("매출이 높은 순으로 요청한 개수만큼 반환한다")
        void returnsTopStoresInDescendingOrder() {
            // Arrange
            salesRankingRepository.addAmount(SALES_DATE, 1L, 5000);
            salesRankingRepository.addAmount(SALES_DATE, 2L, 12000);
            salesRankingRepository.addAmount(SALES_DATE, 3L, 8000);

            // Act
            List<StoreSalesRank> ranks = salesRankingRepository.findTop(SALES_DATE, 2);

            // Assert
            assertThat(ranks).containsExactly(
                    new StoreSalesRank(2L, 12000L),
                    new StoreSalesRank(3L, 8000L));
        }

        @Test
        @DisplayName("집계된 매장이 없으면 빈 목록을 반환한다")
        void returnsEmptyWhenNoSales() {
            // Arrange & Act
            List<StoreSalesRank> ranks = salesRankingRepository.findTop(SALES_DATE, 5);

            // Assert
            assertThat(ranks).isEmpty();
        }
    }

    @Nested
    @DisplayName("매출을 덮어쓸 때, ")
    class ReplaceAmount {

        @Test
        @DisplayName("여러 번 덮어써도 같은 값이 유지된다")
        void isIdempotent() {
            // Arrange
            salesRankingRepository.addAmount(SALES_DATE, 1L, 9000);

            // Act
            salesRankingRepository.replaceAmount(SALES_DATE, 1L, 14000L);
            salesRankingRepository.replaceAmount(SALES_DATE, 1L, 14000L);

            // Assert
            assertThat(salesRankingRepository.findAmount(SALES_DATE, 1L)).contains(14000L);
        }
    }

    @Nested
    @DisplayName("단일 매장 매출을 조회할 때, ")
    class FindAmount {

        @Test
        @DisplayName("집계된 적 없는 매장은 비어 있다")
        void returnsEmptyForUnknownStore() {
            // Arrange & Act
            Optional<Long> amount = salesRankingRepository.findAmount(SALES_DATE, 99L);

            // Assert
            assertThat(amount).isEmpty();
        }
    }
}