package com.sion.pos.domain.store;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreAccount extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "account_name", nullable = false, length = 50)
    private String accountName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    public static StoreAccount create(Long storeId, String accountName, String passwordHash) {
        if (storeId == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "storeId는 필수입니다.");
        }

        if (accountName == null || accountName.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "accountName은 필수입니다.");
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "passwordHash는 필수입니다.");
        }

        StoreAccount account = new StoreAccount();
        account.storeId = storeId;
        account.accountName = normalize(accountName);
        account.passwordHash = passwordHash;

        return account;
    }

    /**
     * 로그인 ID 정규화 규칙. 저장(생성)과 조회(로그인) 양쪽에서 동일하게 적용해야 unique/매칭이 일관된다.
     */
    public static String normalize(String accountName) {
        return accountName.strip().toLowerCase(Locale.ROOT);
    }
}