package com.sion.pos.domain.hq;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HqAccountRepository extends JpaRepository<HqAccount, Long> {

    Optional<HqAccount> findByLoginIdAndDeletedAtIsNull(String loginId);
}