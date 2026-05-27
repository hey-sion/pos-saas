package com.sion.pos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** PortOne 웹훅: 서버-투-서버 호출이라 세션·CSRF 토큰이 없다. 인증/CSRF에서 제외하고 서명 검증으로 인증한다. */
    private static final String WEBHOOK_PATH = "/api/v1/payments/webhook/portone";
    private static final String WEBHOOK_PATTERN = WEBHOOK_PATH + "/**";

    /** 손님 QR 셀프주문: 세션 없이 진입하는 공개 경로. storeId는 URL path로 받는다(사장님 경로의 @LoginStore 세션과 대비). */
    private static final String CUSTOMER_API_PATTERN = "/api/v1/customer/**";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WEBHOOK_PATH, WEBHOOK_PATTERN).permitAll()
                        .requestMatchers(CUSTOMER_API_PATTERN).permitAll()
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        // default-deny: 공개 경로만 위에서 열고 나머지는 전부 인증 필요
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login")
                        .permitAll()
                )
                // 인증 안 된 API 호출은 로그인 페이지로 리다이렉트하지 말고 401을 준다(JSON 클라이언트용). 페이지는 기본 로그인 리다이렉트 유지.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")
                ))
                .csrf(csrf -> csrf
                        // 쿠키 기반 토큰: 서버가 XSRF-TOKEN 쿠키 발급 → JS가 읽어 X-XSRF-TOKEN 헤더로 echo.
                        // 비-XOR 핸들러를 써서 쿠키 토큰 == 기대 토큰이 되도록(헤더 검증 단순화, BREACH 보호는 트레이드오프).
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(WEBHOOK_PATH, WEBHOOK_PATTERN)
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    private static class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
