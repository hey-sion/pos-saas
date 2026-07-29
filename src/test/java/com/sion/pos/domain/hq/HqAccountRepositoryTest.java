package com.sion.pos.domain.hq;

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
class HqAccountRepositoryTest {

    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeHashValue";

    @Autowired private HqAccountRepository hqAccountRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("로그인 ID로 조회 시, ")
    class FindByLoginId {

        @Test
        @DisplayName("삭제되지 않은 본사 계정을 찾는다")
        void findsActiveAccount() {
            // Arrange
            hqAccountRepository.save(HqAccount.create("hq-admin", PASSWORD_HASH));

            // Act
            Optional<HqAccount> found = hqAccountRepository.findByLoginIdAndDeletedAtIsNull("hq-admin");

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getLoginId()).isEqualTo("hq-admin");
        }

        @Test
        @DisplayName("삭제된 계정은 조회되지 않는다")
        void ignoresSoftDeletedAccount() {
            // Arrange
            HqAccount account = hqAccountRepository.save(HqAccount.create("hq-admin", PASSWORD_HASH));
            account.delete();
            hqAccountRepository.save(account);

            // Act
            Optional<HqAccount> found = hqAccountRepository.findByLoginIdAndDeletedAtIsNull("hq-admin");

            // Assert
            assertThat(found).isEmpty();
        }
    }
}