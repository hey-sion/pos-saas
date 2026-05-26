package com.sion.pos.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.sion.pos.support.DatabaseCleanUp;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StoreAccountRepositoryTest {

    private static final Long STORE_ID = 1L;
    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeHashValue";

    @Autowired private StoreAccountRepository storeAccountRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("계정명으로 조회 시, ")
    class FindByAccountName {

        @Test
        @DisplayName("삭제되지 않은 계정을 찾는다")
        void findsActiveAccount() {
            storeAccountRepository.save(StoreAccount.create(STORE_ID, "smile-cafe", PASSWORD_HASH));

            Optional<StoreAccount> found = storeAccountRepository.findByAccountNameAndDeletedAtIsNull("smile-cafe");

            assertThat(found).isPresent();
            assertThat(found.get().getStoreId()).isEqualTo(STORE_ID);
        }

        @Test
        @DisplayName("soft delete된 계정은 조회되지 않는다")
        void ignoresSoftDeletedAccount() {
            StoreAccount account = StoreAccount.create(STORE_ID, "smile-cafe", PASSWORD_HASH);
            account.delete();
            storeAccountRepository.save(account);

            Optional<StoreAccount> found = storeAccountRepository.findByAccountNameAndDeletedAtIsNull("smile-cafe");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 계정명이면 빈 Optional을 반환한다")
        void returnsEmptyWhenNotExists() {
            Optional<StoreAccount> found = storeAccountRepository.findByAccountNameAndDeletedAtIsNull("nobody");

            assertThat(found).isEmpty();
        }
    }
}