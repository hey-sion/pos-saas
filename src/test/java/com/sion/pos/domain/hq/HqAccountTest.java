package com.sion.pos.domain.hq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.PosApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HqAccountTest {

    private static final String LOGIN_ID = "hq-admin";
    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeHashValue";

    @Nested
    @DisplayName("본사 계정 생성 시, ")
    class Create {

        @Test
        @DisplayName("필수 값으로 계정을 생성한다")
        void createsAccount() {
            // Arrange & Act
            HqAccount account = HqAccount.create(LOGIN_ID, PASSWORD_HASH);

            // Assert
            assertThat(account.getLoginId()).isEqualTo(LOGIN_ID);
            assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        }

        @Test
        @DisplayName("loginId를 소문자로 정규화한다")
        void normalizesLoginIdToLowerCase() {
            // Arrange & Act — 매장 계정과 같은 로그인 창을 쓰므로 정규화 규칙도 같아야 한다
            HqAccount account = HqAccount.create("HQ-Admin", PASSWORD_HASH);

            // Assert
            assertThat(account.getLoginId()).isEqualTo(LOGIN_ID);
        }

        @Test
        @DisplayName("loginId가 없으면 예외가 발생한다")
        void throwsWhenLoginIdIsBlank() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> HqAccount.create("  ", PASSWORD_HASH))
                    .isInstanceOf(PosApplicationException.class);
        }

        @Test
        @DisplayName("passwordHash가 없으면 예외가 발생한다")
        void throwsWhenPasswordHashIsBlank() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> HqAccount.create(LOGIN_ID, "  "))
                    .isInstanceOf(PosApplicationException.class);
        }
    }
}