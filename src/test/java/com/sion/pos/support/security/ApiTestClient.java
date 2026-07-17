package com.sion.pos.support.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * E2E 테스트용 클라이언트 팩토리. 자체 소유 TestRestTemplate를 만들어
 * (1) 리다이렉트를 따라가지 않고(login 302의 Set-Cookie를 읽기 위함)
 * (2) form login 후 세션 쿠키 + CSRF 토큰을 모든 요청에 자동 첨부한다.
 * 공유 빈을 변형하지 않으므로 테스트 간 오염이 없다.
 */
public final class ApiTestClient {

    private ApiTestClient() {
    }

    /** 인증 없이 baseUrl을 가리키는 클라이언트(공개 경로/401 검증용). */
    public static TestRestTemplate plain(int port) {
        TestRestTemplate rest = new TestRestTemplate();
        RestTemplate delegate = rest.getRestTemplate();
        delegate.setRequestFactory(nonFollowingFactory());
        delegate.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));
        return rest;
    }

    /** form login으로 인증한 뒤, 이후 모든 요청에 세션 쿠키 + CSRF 토큰을 자동 첨부하는 클라이언트. */
    public static TestRestTemplate loggedIn(int port, String username, String password) {
        TestRestTemplate rest = plain(port);
        Map<String, String> cookies = performLogin(rest, username, password);
        String token = cookies.getOrDefault("XSRF-TOKEN", "");
        String cookieHeader = cookieHeader(cookies);

        rest.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add(HttpHeaders.COOKIE, cookieHeader);
            request.getHeaders().add("X-XSRF-TOKEN", token);
            return execution.execute(request, body);
        });

        return rest;
    }

    /** form login 후 인증 세션 쿠키(Cookie 헤더 값)를 돌려준다. SSE 등 raw 스트림 연결용. */
    public static String sessionCookieHeader(int port, String username, String password) {
        return cookieHeader(performLogin(plain(port), username, password));
    }

    /** GET /login → POST /login 흐름을 수행하고 세션 쿠키 + 회전된 CSRF 토큰을 담은 쿠키 맵을 돌려준다. */
    private static Map<String, String> performLogin(TestRestTemplate rest, String username, String password) {
        // 1) GET /login → XSRF-TOKEN 쿠키 획득
        ResponseEntity<String> loginPage = rest.getForEntity("/login", String.class);
        Map<String, String> cookies = parseSetCookies(loginPage.getHeaders());
        String csrf = require(cookies.get("XSRF-TOKEN"), "/login 응답의 XSRF-TOKEN 쿠키");

        // 2) POST /login (form) — 쿠키 + _csrf 동봉
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookieHeader(cookies));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        form.add("_csrf", csrf);
        ResponseEntity<Void> loginResult = rest.exchange("/login", HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);

        if (!loginResult.getStatusCode().is3xxRedirection()) {
            throw new IllegalStateException("로그인 실패: status=" + loginResult.getStatusCode());
        }

        String location = String.valueOf(loginResult.getHeaders().getLocation());
        if (location.contains("error")) {
            throw new IllegalStateException("로그인 실패(자격 증명/CSRF): location=" + location);
        }

        // 3) 로그인 성공 시 세션·CSRF 토큰이 회전된다 → 응답의 Set-Cookie로 갱신
        cookies.putAll(parseSetCookies(loginResult.getHeaders()));
        return cookies;
    }

    private static ClientHttpRequestFactory nonFollowingFactory() {
        // HttpClient5: PATCH 지원 + 리다이렉트 비추적(login 302의 Set-Cookie를 읽기 위함)
        // + 쿠키 자동관리 비활성(인터셉터가 넣는 수동 Cookie 헤더를 그대로 전송).
        CloseableHttpClient httpClient = HttpClients.custom()
                                                    .disableRedirectHandling()
                                                    .disableCookieManagement()
                                                    .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    private static Map<String, String> parseSetCookies(HttpHeaders headers) {
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
            // 토큰 회전 시 내려오는 삭제 쿠키(빈 값)로 유효 토큰을 덮어쓰지 않는다.
            if (!value.isEmpty()) {
                cookies.put(name, value);
            }
        }

        return cookies;
    }

    private static String cookieHeader(Map<String, String> cookies) {
        return cookies.entrySet().stream()
                      .map(e -> e.getKey() + "=" + e.getValue())
                      .reduce((a, b) -> a + "; " + b)
                      .orElse("");
    }

    private static String require(String value, String what) {
        if (value == null) {
            throw new IllegalStateException(what + "를 찾을 수 없습니다.");
        }

        return value;
    }
}