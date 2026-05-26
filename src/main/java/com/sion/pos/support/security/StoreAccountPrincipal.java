package com.sion.pos.support.security;

import com.sion.pos.domain.store.StoreAccount;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 인증된 매장 계정 principal. storeId는 이 principal에서만 나온다(클라이언트가 주장하는 storeId 신뢰 금지).
 * accountId도 함께 노출해 이후 createdBy/감사 로그에 사용한다. role은 아직 두지 않는다(ADR 0005).
 */
@Getter
public class StoreAccountPrincipal implements UserDetails {

    private final Long accountId;
    private final Long storeId;
    private final String loginId;
    private final String passwordHash;

    public StoreAccountPrincipal(StoreAccount account) {
        this.accountId = account.getId();
        this.storeId = account.getStoreId();
        this.loginId = account.getLoginId();
        this.passwordHash = account.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}