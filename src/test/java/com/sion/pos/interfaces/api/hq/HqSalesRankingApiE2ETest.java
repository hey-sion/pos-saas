package com.sion.pos.interfaces.api.hq;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.hq.HqAccount;
import com.sion.pos.domain.hq.HqAccountRepository;
import com.sion.pos.domain.order.SalesRankingRepository;
import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.domain.store.StoreAccountRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cluster")
class HqSalesRankingApiE2ETest {

    private static final String RAW_PASSWORD = "pw-1234";
    private static final LocalDate SALES_DATE = LocalDate.of(2026, 7, 27);

    @LocalServerPort private int port;

    @Autowired private HqAccountRepository hqAccountRepository;
    @Autowired private StoreAccountRepository storeAccountRepository;
    @Autowired private SalesRankingRepository salesRankingRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        // Redis 를 다른 테스트와 공유하므로 남은 순위 위에 더해지지 않게 비우고 시작한다
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        String hash = passwordEncoder.encode(RAW_PASSWORD);
        hqAccountRepository.save(HqAccount.create("hq-admin", hash));
        storeAccountRepository.save(StoreAccount.create(1L, "smile-cafe", hash));

        salesRankingRepository.addAmount(SALES_DATE, 1L, 5000);
        salesRankingRepository.addAmount(SALES_DATE, 2L, 12000);
        salesRankingRepository.addAmount(SALES_DATE, 3L, 8000);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final String RANKING_PATH = "/api/v1/hq/sales-ranking?date=" + SALES_DATE + "&limit=2";

    private ResponseEntity<List<Map<String, Object>>> getRanking(TestRestTemplate rest) {
        return rest.exchange(RANKING_PATH, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
        });
    }

    /** 거부 응답은 본문이 없어 목록으로 읽을 수 없다 — 상태 코드만 본다. */
    private ResponseEntity<String> getRankingRaw(TestRestTemplate rest) {
        return rest.exchange(RANKING_PATH, HttpMethod.GET, null, String.class);
    }

    @Nested
    @DisplayName("전국 매장 매출 순위 조회는, ")
    class Authorization {

        @Test
        @DisplayName("본사 계정에만 허용된다")
        void allowsHqAccount() {
            // Arrange
            TestRestTemplate rest = ApiTestClient.loggedIn(port, "hq-admin", RAW_PASSWORD);

            // Act
            ResponseEntity<String> response = getRankingRaw(rest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("매장 계정에는 허용되지 않는다")
        void forbidsStoreAccount() {
            // Arrange — 자기 매장만 보던 계정이 전 매장을 가로질러 보면 안 된다
            TestRestTemplate rest = ApiTestClient.loggedIn(port, "smile-cafe", RAW_PASSWORD);

            // Act
            ResponseEntity<String> response = getRankingRaw(rest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("로그인하지 않으면 인증을 요구한다")
        void requiresAuthentication() {
            // Arrange
            TestRestTemplate rest = ApiTestClient.plain(port);

            // Act
            ResponseEntity<String> response = getRankingRaw(rest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("본사 계정이 전국 매장 매출 순위를 조회하면, ")
    class GetSalesRanking {

        @Test
        @DisplayName("매출이 높은 순으로 요청한 개수만큼 받는다")
        void returnsTopStoresInDescendingOrder() {
            // Arrange
            TestRestTemplate rest = ApiTestClient.loggedIn(port, "hq-admin", RAW_PASSWORD);

            // Act
            ResponseEntity<List<Map<String, Object>>> response = getRanking(rest);

            // Assert
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0)).containsEntry("storeId", 2)
                    .containsEntry("salesAmount", 12000);
            assertThat(response.getBody().get(1)).containsEntry("storeId", 3)
                    .containsEntry("salesAmount", 8000);
        }
    }
}