package com.sion.pos.support.security;

import com.sion.pos.domain.hq.HqAccount;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class HqAccountPrincipal implements UserDetails {

    public static final String ROLE = "ROLE_HQ";

    private final Long accountId;
    private final String loginId;
    private final String passwordHash;

    public HqAccountPrincipal(HqAccount account) {
        this.accountId = account.getId();
        this.loginId = account.getLoginId();
        this.passwordHash = account.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
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