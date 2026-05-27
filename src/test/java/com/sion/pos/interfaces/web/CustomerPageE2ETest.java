package com.sion.pos.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerPageE2ETest {

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("손님 주문 화면 진입 시, ")
    class Order {

        @Test
        @DisplayName("로그인 없이 접근하면 200 OK와 매장명이 렌더된 화면을 반환한다.")
        void returnsPublicPageWithStoreName() {
            Store store = storeRepository.save(Store.create("스마일 분식", "010-1234-5678"));

            ResponseEntity<String> response = ApiTestClient.plain(port)
                    .getForEntity("/order/" + store.getId(), String.class);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).contains("스마일 분식"),
                    () -> assertThat(response.getBody()).contains("/js/customer/order.js")
            );
        }
    }
}