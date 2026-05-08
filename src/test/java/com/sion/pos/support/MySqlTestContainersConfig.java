package com.sion.pos.support;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class MySqlTestContainersConfig {

    private static final MySQLContainer<?> CONTAINER;

    static {
        CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                        .withDatabaseName("pos_saas_test")
                        .withUsername("test")
                        .withPassword("test")
                        .withCommand(
                                "--character-set-server=utf8mb4",
                                "--collation-server=utf8mb4_unicode_ci"
                        );

        CONTAINER.start();

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul",
                CONTAINER.getHost(),
                CONTAINER.getFirstMappedPort(),
                CONTAINER.getDatabaseName()
        );

        System.setProperty("SPRING_DATASOURCE_URL", jdbcUrl);
        System.setProperty("SPRING_DATASOURCE_USERNAME", CONTAINER.getUsername());
        System.setProperty("SPRING_DATASOURCE_PASSWORD", CONTAINER.getPassword());
    }
}