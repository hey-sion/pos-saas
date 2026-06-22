package com.sion.pos.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.portone.PortOneProperties;
import com.sion.pos.support.security.ApiTestClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    @Autowired private MenuRepository menuRepository;
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
                    .getForEntity("/api/v1/orders/waiting", String.class);

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
                    client.getForEntity("/api/v1/orders/waiting", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("로그인한 매장의 매장 정보와 메뉴만 조회된다.")
        void returnsOnlyLoggedInStoreData() {
            Store firstStore = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            Store secondStore = storeRepository.save(Store.create("2번 테스트 매장", "010-2222-2222"));
            menuRepository.save(Menu.create(firstStore.getId(), "1번 메뉴", 1_000, 1));
            menuRepository.save(Menu.create(secondStore.getId(), "2번 메뉴", 2_000, 1));
            storeAccountService.register(secondStore.getId(), "second-owner", PASSWORD);

            TestRestTemplate client = ApiTestClient.loggedIn(port, "second-owner", PASSWORD);

            ResponseEntity<String> storeResponse = client.getForEntity("/api/stores/me", String.class);
            ResponseEntity<String> menuResponse = client.getForEntity("/api/menus", String.class);

            assertThat(storeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(storeResponse.getBody()).contains("2번 테스트 매장");
            assertThat(storeResponse.getBody()).doesNotContain("1번 테스트 매장");
            assertThat(menuResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(menuResponse.getBody()).contains("2번 메뉴");
            assertThat(menuResponse.getBody()).doesNotContain("1번 메뉴");
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

        @Test
        @DisplayName("로그아웃 후 보호 페이지 접근시 로그인 페이지로 리다이렉트된다.")
        void redirectsToLoginAfterLogout() {
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);

            TestRestTemplate rest = ApiTestClient.plain(port);
            Map<String, String> cookies = loginAndLoadMainPage(rest, "owner", PASSWORD);

            ResponseEntity<Void> logoutResult = logout(rest, cookies);
            assertThat(logoutResult.getStatusCode().is3xxRedirection()).isTrue();
            assertThat(logoutResult.getHeaders().getLocation().toString()).endsWith("/login");
            // 로그아웃 응답이 취소한 쿠키(자동 로그인 쿠키 포함)를 브라우저처럼 폐기한다.
            removeCancelledCookies(cookies, logoutResult.getHeaders());

            ResponseEntity<String> protectedPage = getWithCookies(rest, "/", cookies);
            assertThat(protectedPage.getStatusCode().is3xxRedirection()).isTrue();
            assertThat(protectedPage.getHeaders().getLocation().toString()).contains("/login");
        }
    }

    @Nested
    @DisplayName("자동 로그인(remember-me) 설정 시, ")
    class RememberMe {

        @Test
        @DisplayName("로그인하면 자동 로그인 쿠키가 발급된다.")
        void issuesRememberMeCookieOnLogin() {
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);

            String rememberMeCookie = loginAndGetRememberMeCookie(ApiTestClient.plain(port), "owner", PASSWORD);

            assertThat(rememberMeCookie).isNotBlank();
        }

        @Test
        @DisplayName("세션 쿠키 없이 자동 로그인 쿠키만으로 보호된 API에 접근할 수 있다.")
        void canAccessApiWithRememberMeCookieOnly() {
            Store store = storeRepository.save(Store.create("테스트 매장", "010-0000-0000"));
            storeAccountService.register(store.getId(), "owner", PASSWORD);

            TestRestTemplate rest = ApiTestClient.plain(port);
            String rememberMeCookie = loginAndGetRememberMeCookie(rest, "owner", PASSWORD);

            // 세션(JSESSIONID) 없이 remember-me 쿠키만 동봉 → 자동 인증되어야 한다.
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, "remember-me=" + rememberMeCookie);
            ResponseEntity<String> response = rest.exchange(
                    "/api/v1/orders/waiting", HttpMethod.GET, new HttpEntity<>(headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private Map<String, String> loginAndLoadMainPage(TestRestTemplate rest, String username, String password) {
        ResponseEntity<String> loginPage = rest.getForEntity("/login", String.class);
        Map<String, String> cookies = parseSetCookies(loginPage.getHeaders());
        String csrf = cookies.get("XSRF-TOKEN");

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        loginHeaders.add(HttpHeaders.COOKIE, cookieHeader(cookies));
        MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
        loginForm.add("username", username);
        loginForm.add("password", password);
        loginForm.add("_csrf", csrf);

        ResponseEntity<Void> loginResult =
                rest.exchange("/login", HttpMethod.POST, new HttpEntity<>(loginForm, loginHeaders), Void.class);
        assertThat(loginResult.getStatusCode().is3xxRedirection()).isTrue();
        cookies.putAll(parseSetCookies(loginResult.getHeaders()));
        cookies.remove("XSRF-TOKEN");

        ResponseEntity<String> mainPage = getWithCookies(rest, "/", cookies);
        assertThat(mainPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        cookies.putAll(parseSetCookies(mainPage.getHeaders()));

        return cookies;
    }

    private String loginAndGetRememberMeCookie(TestRestTemplate rest, String username, String password) {
        ResponseEntity<String> loginPage = rest.getForEntity("/login", String.class);
        Map<String, String> cookies = parseSetCookies(loginPage.getHeaders());
        String csrf = cookies.get("XSRF-TOKEN");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookieHeader(cookies));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        form.add("_csrf", csrf);

        ResponseEntity<Void> loginResult =
                rest.exchange("/login", HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);
        assertThat(loginResult.getStatusCode().is3xxRedirection()).isTrue();

        return parseSetCookies(loginResult.getHeaders()).get("remember-me");
    }

    /** 응답이 취소한 쿠키(빈 값 또는 Max-Age=0)를 브라우저처럼 보관 목록에서 제거한다. */
    private void removeCancelledCookies(Map<String, String> cookies, HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return;
        }

        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
                cookies.remove(name);
            }
        }
    }

    private ResponseEntity<Void> logout(TestRestTemplate rest, Map<String, String> cookies) {
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        logoutHeaders.add(HttpHeaders.COOKIE, cookieHeader(cookies));
        MultiValueMap<String, String> logoutForm = new LinkedMultiValueMap<>();
        logoutForm.add("_csrf", cookies.get("XSRF-TOKEN"));

        return rest.exchange("/logout", HttpMethod.POST, new HttpEntity<>(logoutForm, logoutHeaders), Void.class);
    }

    private ResponseEntity<String> getWithCookies(TestRestTemplate rest, String path, Map<String, String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader(cookies));

        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Map<String, String> parseSetCookies(HttpHeaders headers) {
        Map<String, String> cookies = new LinkedHashMap<>();
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);

        if (setCookies == null) {
            return cookies;
        }

        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (!value.isEmpty()) {
                cookies.put(name, value);
            }
        }

        return cookies;
    }

    private String cookieHeader(Map<String, String> cookies) {
        return cookies.entrySet().stream()
                      .map(e -> e.getKey() + "=" + e.getValue())
                      .reduce((a, b) -> a + "; " + b)
                      .orElse("");
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
