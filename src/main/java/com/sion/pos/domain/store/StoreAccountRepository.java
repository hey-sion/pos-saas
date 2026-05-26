package com.sion.pos.domain.store;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAccountRepository extends JpaRepository<StoreAccount, Long> {

    Optional<StoreAccount> findByLoginIdAndDeletedAtIsNull(String loginId);
}