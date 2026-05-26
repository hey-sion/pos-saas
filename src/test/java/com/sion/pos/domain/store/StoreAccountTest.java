package com.sion.pos.domain.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StoreAccountTest {

    private static final Long STORE_ID = 1L;
    private static final String ACCOUNT_NAME = "smile-cafe";
    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeHashValue";

    @Nested
    @DisplayName("매장 계정 생성 시, ")
    class Create {

        @Test
        @DisplayName("필수 값으로 계정을 생성한다")
        void createsAccount() {
            StoreAccount account = StoreAccount.create(STORE_ID, ACCOUNT_NAME, PASSWORD_HASH);

            assertThat(account.getStoreId()).isEqualTo(STORE_ID);
            assertThat(account.getAccountName()).isEqualTo(ACCOUNT_NAME);
            assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        }

        @Test
        @DisplayName("accountName을 소문자로 정규화한다")
        void normalizesAccountNameToLowerCase() {
            StoreAccount account = StoreAccount.create(STORE_ID, "Smile-CAFE", PASSWORD_HASH);

            assertThat(account.getAccountName()).isEqualTo("smile-cafe");
        }

        @Test
        @DisplayName("accountName 앞뒤 공백을 제거한다")
        void stripsAccountName() {
            StoreAccount account = StoreAccount.create(STORE_ID, "  smile-cafe  ", PASSWORD_HASH);

            assertThat(account.getAccountName()).isEqualTo("smile-cafe");
        }

        @Test
        @DisplayName("storeId가 null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenStoreIdIsNull() {
            expects(ErrorType.BAD_REQUEST, () -> StoreAccount.create(null, ACCOUNT_NAME, PASSWORD_HASH));
        }

        @Test
        @DisplayName("accountName이 null 또는 공백이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenAccountNameIsBlank() {
            expects(ErrorType.BAD_REQUEST, () -> StoreAccount.create(STORE_ID, null, PASSWORD_HASH));
            expects(ErrorType.BAD_REQUEST, () -> StoreAccount.create(STORE_ID, "   ", PASSWORD_HASH));
        }

        @Test
        @DisplayName("passwordHash가 null 또는 공백이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPasswordHashIsBlank() {
            expects(ErrorType.BAD_REQUEST, () -> StoreAccount.create(STORE_ID, ACCOUNT_NAME, null));
            expects(ErrorType.BAD_REQUEST, () -> StoreAccount.create(STORE_ID, ACCOUNT_NAME, "   "));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}