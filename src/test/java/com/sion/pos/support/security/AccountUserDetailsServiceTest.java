package com.sion.pos.support.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.hq.HqAccount;
import com.sion.pos.domain.hq.HqAccountRepository;
import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.domain.store.StoreAccountRepository;
import com.sion.pos.support.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@SpringBootTest
class AccountUserDetailsServiceTest {

    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeHashValue";

    @Autowired private UserDetailsService userDetailsService;
    @Autowired private StoreAccountRepository storeAccountRepository;
    @Autowired private HqAccountRepository hqAccountRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private boolean hasAuthority(UserDetails user, String authority) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    @Nested
    @DisplayName("로그인 계정을 조회할 때, ")
    class LoadUserByUsername {

        @Test
        @DisplayName("매장 계정은 매장 권한을 갖는다")
        void loadsStoreAccountWithStoreRole() {
            // Arrange
            storeAccountRepository.save(StoreAccount.create(1L, "smile-cafe", PASSWORD_HASH));

            // Act
            UserDetails user = userDetailsService.loadUserByUsername("smile-cafe");

            // Assert
            assertThat(user).isInstanceOf(StoreAccountPrincipal.class);
            assertThat(hasAuthority(user, "ROLE_STORE")).isTrue();
            assertThat(hasAuthority(user, "ROLE_HQ")).isFalse();
        }

        @Test
        @DisplayName("본사 계정은 본사 권한을 갖는다")
        void loadsHqAccountWithHqRole() {
            // Arrange
            hqAccountRepository.save(HqAccount.create("hq-admin", PASSWORD_HASH));

            // Act
            UserDetails user = userDetailsService.loadUserByUsername("hq-admin");

            // Assert
            assertThat(user).isInstanceOf(HqAccountPrincipal.class);
            assertThat(hasAuthority(user, "ROLE_HQ")).isTrue();
            assertThat(hasAuthority(user, "ROLE_STORE")).isFalse();
        }

        @Test
        @DisplayName("어느 쪽에도 없는 계정이면 예외가 발생한다")
        void throwsWhenAccountNotFound() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }
}