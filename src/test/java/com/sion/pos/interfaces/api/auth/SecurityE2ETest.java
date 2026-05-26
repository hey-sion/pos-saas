package com.sion.pos.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.portone.PortOneProperties;
import com.sion.pos.support.security.ApiTestClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityE2ETest {

    private static final String PASSWORD = "password1!";

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreAccountService storeAccountService;
    @Autowired private PortOneProperties portOneProperties;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("인증 없이 접근 시, ")
    class WithoutAuthentication {

        @Test
        @DisplayName("로그인 페이지는 200 OK로 열린다.")
        void loginPageIsPublic() {
            ResponseEntity<String> response = ApiTestClient.plain(port).getForEntity("/login", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("보호된 페이지(/)는 로그인 페이지로 리다이렉트된다.")
        void protectedPageRedirectsToLogin() {
            ResponseEntity<String> response = ApiTestClient.plain(port).getForEntity("/", String.class);

            assertThat(response.getStatusCode().is3xxRedirection()).isTrue();
            assertThat(response.getHeaders().getLocation().toString()).contains("/login");
        }

        @Test
        @DisplayName("보호된 API는 401 Unauthorized를 반환한다.")
        void protectedApiReturnsUnauthorized() {
            ResponseEntity<String> response = ApiTestClient.plain(port)
                    .getForEntity("/api/v1/orders/waiting?storeId=1", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("PortOne 웹훅은 인증/CSRF 없이도 서명만 유효하면 200 OK로 처리된다.")
        void webhookIsPublicWithValidSignature() {
            String body = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"unknown-payment-id\"}}";

            ResponseEntity<Void> response = ApiTestClient.plain(port).exchange(
                    "/api/v1/payments/webhook/portone", HttpMethod.POST,
                    new HttpEntity<>(body, signedHeaders(body)), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("로그인 시, ")
    class Login {

        @Test
        @DisplayName("올바른 계정으로 로그인하면 해당 매장의 API에 접근할 수 있다.")
        void canAccessApiAfterLogin() {
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);

            TestRestTemplate client = ApiTestClient.loggedIn(port, "owner", PASSWORD);
            ResponseEntity<String> response =
                    client.getForEntity("/api/v1/orders/waiting?storeId=" + store.getId(), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("틀린 비밀번호면 에러 파라미터와 함께 로그인 페이지로 리다이렉트된다.")
        void wrongPasswordRedirectsToError() {
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);

            TestRestTemplate rest = ApiTestClient.plain(port);
            ResponseEntity<String> loginPage = rest.getForEntity("/login", String.class);
            String csrf = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                                   .filter(c -> c.startsWith("XSRF-TOKEN="))
                                   .map(c -> c.split(";", 2)[0].substring("XSRF-TOKEN=".length()))
                                   .findFirst()
                                   .orElseThrow();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", "owner");
            form.add("password", "wrong-password");
            form.add("_csrf", csrf);

            ResponseEntity<Void> response =
                    rest.exchange("/login", HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);

            assertThat(response.getStatusCode().is3xxRedirection()).isTrue();
            assertThat(response.getHeaders().getLocation().toString()).contains("error");
        }
    }

    private HttpHeaders signedHeaders(String body) {
        String msgId = "msg_" + UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("webhook-id", msgId);
        headers.set("webhook-timestamp", timestamp);
        headers.set("webhook-signature", sign(msgId, timestamp, body));
        return headers;
    }

    private String sign(String msgId, String timestamp, String body) {
        try {
            String secret = portOneProperties.webhookSecret();
            byte[] key = Base64.getDecoder().decode(
                    secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] signature = mac.doFinal((msgId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}