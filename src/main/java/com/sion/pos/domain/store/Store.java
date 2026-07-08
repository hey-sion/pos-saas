package com.sion.pos.domain.store;

import com.sion.pos.domain.BaseEntity;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 30, unique = true)
    private String slug;

    @Embedded
    private BusinessInfo businessInfo;

    @Column(name = "kakao_pay_live", nullable = false)
    private boolean kakaoPayLive;

    public static Store create(String name, String phone) {
        if (name == null || name.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "name은 필수입니다.");
        }

        Store store = new Store();
        store.name = name;
        store.phone = phone;

        return store;
    }

    public void enableQrOrder(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "고객 주문 페이지 생성시 slug는 필수입니다.");
        }

        this.slug = slug;
    }

    public boolean isQrOrderEnabled() {
        return slug != null;
    }

    public void enableKakaoPayLive() {
        this.kakaoPayLive = true;
    }

    public void disableKakaoPayLive() {
        this.kakaoPayLive = false;
    }

    public void updateBusinessInfo(BusinessInfo businessInfo) {
        if (businessInfo == null) {
            throw new PosApplicationException(ErrorType.BAD_REQUEST, "businessInfo는 필수입니다.");
        }

        this.businessInfo = businessInfo;
    }
}