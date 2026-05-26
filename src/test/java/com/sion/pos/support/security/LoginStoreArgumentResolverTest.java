package com.sion.pos.support.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LoginStoreArgumentResolverTest {

    private final LoginStoreArgumentResolver resolver = new LoginStoreArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("supportsParameter는")
    class SupportsParameter {

        @Test
        @DisplayName("@LoginStore Long 파라미터만 지원한다.")
        void supportsOnlyLoginStoreLongParameter() throws NoSuchMethodException {
            assertThat(resolver.supportsParameter(parameter("loginStoreLong", 0))).isTrue();
            assertThat(resolver.supportsParameter(parameter("plainLong", 0))).isFalse();
            assertThat(resolver.supportsParameter(parameter("loginStoreString", 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveArgument는")
    class ResolveArgument {

        @Test
        @DisplayName("인증된 매장 계정 principal의 storeId를 반환한다.")
        void resolvesStoreIdFromPrincipal() throws Exception {
            StoreAccount account = StoreAccount.create(2L, "owner", "password-hash");
            StoreAccountPrincipal principal = new StoreAccountPrincipal(account);
            SecurityContextHolder.getContext()
                                 .setAuthentication(new TestingAuthenticationToken(principal, principal.getPassword()));

            Object resolved = resolver.resolveArgument(parameter("loginStoreLong", 0), null, null, null);

            assertThat(resolved).isEqualTo(2L);
        }

        @Test
        @DisplayName("인증 정보가 없으면 UNAUTHORIZED 예외를 발생시킨다.")
        void throwsWhenAuthenticationIsMissing() throws Exception {
            assertThatThrownBy(() -> resolver.resolveArgument(parameter("loginStoreLong", 0), null, null, null))
                    .isInstanceOfSatisfying(PosApplicationException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @Test
        @DisplayName("principal 타입이 매장 계정이 아니면 UNAUTHORIZED 예외를 발생시킨다.")
        void throwsWhenPrincipalTypeIsInvalid() throws Exception {
            SecurityContextHolder.getContext()
                                 .setAuthentication(new TestingAuthenticationToken("anonymousUser", null));

            assertThatThrownBy(() -> resolver.resolveArgument(parameter("loginStoreLong", 0), null, null, null))
                    .isInstanceOfSatisfying(PosApplicationException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }

    private MethodParameter parameter(String methodName, int index) throws NoSuchMethodException {
        Method method = ControllerMethodSamples.class.getDeclaredMethod(methodName, methodParameterTypes(methodName));
        return new MethodParameter(method, index);
    }

    private Class<?>[] methodParameterTypes(String methodName) {
        return switch (methodName) {
            case "loginStoreLong", "plainLong" -> new Class<?>[] {Long.class};
            case "loginStoreString" -> new Class<?>[] {String.class};
            default -> throw new IllegalArgumentException("알 수 없는 메서드입니다: " + methodName);
        };
    }

    @SuppressWarnings("unused")
    private static class ControllerMethodSamples {

        void loginStoreLong(@LoginStore Long storeId) {
        }

        void plainLong(Long storeId) {
        }

        void loginStoreString(@LoginStore String storeId) {
        }
    }
}