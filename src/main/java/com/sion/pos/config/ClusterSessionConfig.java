package com.sion.pos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * cluster 프로파일 전용 세션 설정. 세션을 Redis 에 저장(인스턴스 공유)하고 principal(loginId)
 * 색인으로 계정별 강제 무효화를 가능케 한다. 기본 프로파일은 로드되지 않아 인메모리 세션을 유지한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("cluster")
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = ClusterSessionConfig.SESSION_TTL_SECONDS)
public class ClusterSessionConfig {

    // 기본 30일 유지
    static final int SESSION_TTL_SECONDS = 30 * 24 * 60 * 60;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        // 브라우저를 닫아도 유지되는 persistent 쿠키
        serializer.setCookieMaxAge(SESSION_TTL_SECONDS);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}