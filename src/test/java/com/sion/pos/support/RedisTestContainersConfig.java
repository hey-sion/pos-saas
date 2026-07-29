package com.sion.pos.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * cluster 프로파일 테스트가 공유하는 Redis 컨테이너.
 * 테스트마다 컨테이너를 띄우고 @DynamicPropertySource 로 포트를 주면 프로퍼티가 달라져
 * 스프링 컨텍스트가 매번 새로 뜬다 → 컨텍스트 수만큼 DB 커넥션 풀이 늘어 연결이 고갈된다.
 */
@Configuration
@Profile("cluster")
public class RedisTestContainersConfig {

    private static final GenericContainer<?> CONTAINER;

    static {
        CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);

        CONTAINER.start();

        System.setProperty("SPRING_DATA_REDIS_HOST", CONTAINER.getHost());
        System.setProperty("SPRING_DATA_REDIS_PORT", String.valueOf(CONTAINER.getMappedPort(6379)));
    }
}