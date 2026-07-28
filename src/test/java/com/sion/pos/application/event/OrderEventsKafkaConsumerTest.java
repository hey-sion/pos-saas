package com.sion.pos.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.domain.order.StoreDailySales;
import com.sion.pos.domain.order.StoreDailySalesRepository;
import com.sion.pos.support.DatabaseCleanUp;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("cluster")
@Testcontainers
class OrderEventsKafkaConsumerTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void clusterProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private OutboxEventPublisher outboxEventPublisher;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private StoreDailySalesRepository storeDailySalesRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("주문완료 봉투가 Kafka를 거쳐 소비되어 매출이 집계된다")
    void aggregatesThroughKafka() {
        // arrange
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 14, 3);
        outboxEventPublisher.publish("ORDER_DELIVERED",
                new OrderDeliveredEvent("ORDER_DELIVERED:3:2026-07-27:2", 3L, occurredAt, 9000));

        // act — 릴레이가 Kafka로 발행, 컨슈머가 비동기로 소비
        outboxRelay.relayPending();

        // assert
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            StoreDailySales sales = storeDailySalesRepository
                    .findByStoreIdAndSalesDate(3L, occurredAt.toLocalDate())
                    .orElseThrow();
            assertThat(sales.getSalesAmount()).isEqualTo(9000L);
        });
    }
}