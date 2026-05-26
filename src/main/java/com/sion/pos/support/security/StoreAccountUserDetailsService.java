package com.sion.pos.support.security;

import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.domain.store.StoreAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoreAccountUserDetailsService implements UserDetailsService {

    private final StoreAccountRepository storeAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        // 저장 시와 동일한 정규화를 적용해 "Smile-CAFE" 입력도 "smile-cafe" 계정과 매칭된다.
        String normalized = StoreAccount.normalize(username);
        StoreAccount account = storeAccountRepository.findByLoginIdAndDeletedAtIsNull(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다: " + username));
        return new StoreAccountPrincipal(account);
    }
}