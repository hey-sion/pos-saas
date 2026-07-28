package com.sion.pos.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.application.order.OrderDeliveredEvent;
import com.sion.pos.domain.event.OutboxRepository;
import com.sion.pos.support.DatabaseCleanUp;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
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
class KafkaOutboxDispatcherTest {

    static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();

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
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("대기 중인 주문완료 봉투를 order-events 토픽으로 발행하고 행을 발행 처리한다")
    void publishesEnvelopeToKafka() throws InterruptedException {
        // arrange
        outboxEventPublisher.publish("ORDER_DELIVERED",
                new OrderDeliveredEvent("ORDER_DELIVERED:3:2026-07-27:2", 3L,
                        LocalDateTime.of(2026, 7, 27, 14, 3), 9000));

        // act
        outboxRelay.relayPending();

        // assert
        String received = RECEIVED.poll(10, TimeUnit.SECONDS);
        assertThat(received)
                .contains("\"type\":\"ORDER_DELIVERED\"")
                .contains("ORDER_DELIVERED:3:2026-07-27:2");
        assertThat(outboxRepository.findAll().get(0).getPublishedAt()).isNotNull();
    }

    @TestConfiguration
    static class TestConsumerConfig {

        @KafkaListener(topics = "order-events", groupId = "test-order-events")
        void listen(String message) {
            RECEIVED.add(message);
        }
    }
}