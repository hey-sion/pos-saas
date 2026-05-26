package com.sion.pos.application.store;

import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.domain.store.StoreAccountRepository;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreAccountService {

    private final StoreAccountRepository storeAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /** raw 비밀번호를 받아 해싱 후 계정을 생성한다. 도메인 엔티티는 해시 문자열만 알고 해싱 방식은 모른다. */
    @Transactional
    public StoreAccount register(Long storeId, String loginId, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "rawPassword는 필수입니다.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        return storeAccountRepository.save(StoreAccount.create(storeId, loginId, passwordHash));
    }
}