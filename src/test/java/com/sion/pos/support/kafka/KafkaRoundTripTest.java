package com.sion.pos.support.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
@DisplayName("Kafka 발행-수신 왕복")
class KafkaRoundTripTest {

    static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    // cluster 프로파일은 Redis 세션도 켜므로 함께 띄운다.
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void clusterProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("보낸 메시지가 리스너에 도달한다")
    void deliversMessageToListener() throws InterruptedException {
        // act
        kafkaTemplate.send("round-trip-topic", "hello-kafka");

        // assert
        String received = RECEIVED.poll(10, TimeUnit.SECONDS);
        assertThat(received).isEqualTo("hello-kafka");
    }

    @TestConfiguration
    static class TestListenerConfig {

        @KafkaListener(topics = "round-trip-topic", groupId = "round-trip-group")
        void listen(String message) {
            RECEIVED.add(message);
        }
    }
}