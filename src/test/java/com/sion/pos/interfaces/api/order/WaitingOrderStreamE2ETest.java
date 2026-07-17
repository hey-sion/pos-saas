package com.sion.pos.interfaces.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sion.pos.application.order.WaitingOrdersNotifier;
import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// 테스트가 남긴 SSE 연결(장기 async 요청) 때문에 컨텍스트 종료 시 graceful shutdown이 대기하지 않도록 즉시 종료.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.shutdown=immediate"
)
class WaitingOrderStreamE2ETest {

    private static final String PASSWORD = "password1!";

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreAccountService storeAccountService;
    @Autowired private WaitingOrdersNotifier notifier;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("대기목록 스트림 구독 시, ")
    @Nested
    class WhenSubscribing {

        @DisplayName("대기목록 변경이 발행되면 해당 매장 화면이 waiting-orders-updated 이벤트를 받는다.")
        @Test
        void receivesEventOnNotify() throws Exception {
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);
            String cookie = ApiTestClient.sessionCookieHeader(port, "owner", PASSWORD);

            List<String> received = new CopyOnWriteArrayList<>();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            try {
                HttpRequest request = HttpRequest.newBuilder(streamUri())
                        .header(HttpHeaders.COOKIE, cookie)
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .GET()
                        .build();
                CompletableFuture<HttpResponse<Stream<String>>> future =
                        client.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
                HttpResponse<Stream<String>> response = future.get(5, TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(200);

                Thread reader = new Thread(() -> response.body().forEach(received::add));
                reader.setDaemon(true);
                reader.start();

                // 스트림 개통(초기 코멘트 수신)을 기다린다 → 이 시점엔 서버 레지스트리 등록이 끝나 있다.
                await().atMost(Duration.ofSeconds(5))
                       .until(() -> received.stream().anyMatch(line -> line.contains("connected")));

                notifier.notifyUpdated(store.getId());

                await().atMost(Duration.ofSeconds(5))
                       .until(() -> received.stream().anyMatch(line -> line.contains("waiting-orders-updated")));
            } finally {
                client.shutdownNow();
            }
        }

        private URI streamUri() {
            return URI.create("http://localhost:" + port + "/api/v1/orders/waiting/stream");
        }
    }
}