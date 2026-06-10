package com.sion.pos.domain.store;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장 사업자 정보. 전자상거래법 표시의무 + PG 심사를 위해 손님 공개 페이지 푸터에 노출된다.
 * 매장(store_id)에 종속된 값이므로 별도 애그리거트가 아닌 Store 내부 값 객체로 둔다.
 * 상호는 Store.name, 고객센터 전화는 Store.phone을 재사용하므로 여기 두지 않는다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessInfo {

    @Column(name = "representative_name", length = 50)
    private String representativeName;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "mail_order_sales_number", length = 50)
    private String mailOrderSalesNumber;

    @Column(name = "business_address", length = 255)
    private String businessAddress;

    @Column(name = "business_email", length = 100)
    private String businessEmail;

    private BusinessInfo(String representativeName, String businessRegistrationNumber,
                         String mailOrderSalesNumber, String businessAddress, String businessEmail) {
        this.representativeName = representativeName;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.mailOrderSalesNumber = mailOrderSalesNumber;
        this.businessAddress = businessAddress;
        this.businessEmail = businessEmail;
    }

    public static BusinessInfo of(String representativeName, String businessRegistrationNumber,
                                  String mailOrderSalesNumber, String businessAddress, String businessEmail) {
        return new BusinessInfo(
                normalize(representativeName),
                normalize(businessRegistrationNumber),
                normalize(mailOrderSalesNumber),
                normalize(businessAddress),
                normalize(businessEmail)
        );
    }

    /**
     * 표시의무 필수 항목(대표자·사업자등록번호·주소)이 모두 채워졌는지.
     * 통신판매업 신고번호와 이메일은 매장 상황에 따라 면제/선택이라 필수에서 제외한다.
     * 고객센터 전화(Store.phone)는 BusinessInfo 밖에 있어 여기서 검증하지 않는다.
     */
    public boolean isDisclosureComplete() {
        return representativeName != null
                && businessRegistrationNumber != null
                && businessAddress != null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}