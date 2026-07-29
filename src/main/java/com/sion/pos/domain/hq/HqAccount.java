package com.sion.pos.domain.hq;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.domain.store.StoreAccount;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hq_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HqAccount extends BaseEntity {

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    public static HqAccount create(String loginId, String passwordHash) {
        if (loginId == null || loginId.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "loginId는 필수입니다.");
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "passwordHash는 필수입니다.");
        }

        HqAccount account = new HqAccount();
        // 매장 계정과 같은 정규화 규칙 사용
        account.loginId = StoreAccount.normalize(loginId);
        account.passwordHash = passwordHash;

        return account;
    }
}