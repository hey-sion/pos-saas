package com.sion.pos.application.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.domain.store.StoreAccountRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class StoreAccountServiceTest {

    private static final Long STORE_ID = 1L;

    @Autowired private StoreAccountService storeAccountService;
    @Autowired private StoreAccountRepository storeAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("계정 등록 시, ")
    class Register {

        @Test
        @DisplayName("raw 비밀번호를 BCrypt로 해싱해 저장한다")
        void hashesPassword() {
            StoreAccount account = storeAccountService.register(STORE_ID, "owner", "password1!");

            StoreAccount persisted = storeAccountRepository.findById(account.getId()).orElseThrow();
            assertThat(persisted.getPasswordHash()).isNotEqualTo("password1!");
            assertThat(passwordEncoder.matches("password1!", persisted.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("loginId를 소문자로 정규화해 저장한다")
        void normalizesLoginId() {
            storeAccountService.register(STORE_ID, "Owner-CAFE", "password1!");

            assertThat(storeAccountRepository.findByLoginIdAndDeletedAtIsNull("owner-cafe")).isPresent();
        }

        @Test
        @DisplayName("raw 비밀번호가 비면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPasswordIsBlank() {
            assertThatThrownBy(() -> storeAccountService.register(STORE_ID, "owner", "  "))
                    .isInstanceOfSatisfying(PosApplicationException.class,
                            e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}