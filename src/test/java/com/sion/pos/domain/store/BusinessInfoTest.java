package com.sion.pos.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BusinessInfoTest {

    @Nested
    @DisplayName("사업자 정보 생성 시, ")
    class Of {

        @Test
        @DisplayName("필수 값으로 사업자 정보를 생성한다")
        void createsBusinessInfo() {
            BusinessInfo info = BusinessInfo.of("홍길동", "211-88-79575", "2022-서울성동-01952", "서울특별시 성동구 아차산로 13길 11", "owner@smile.kr");

            assertThat(info.getRepresentativeName()).isEqualTo("홍길동");
            assertThat(info.getBusinessRegistrationNumber()).isEqualTo("211-88-79575");
            assertThat(info.getMailOrderSalesNumber()).isEqualTo("2022-서울성동-01952");
            assertThat(info.getBusinessAddress()).isEqualTo("서울특별시 성동구 아차산로 13길 11");
            assertThat(info.getBusinessEmail()).isEqualTo("owner@smile.kr");
        }

        @Test
        @DisplayName("각 값의 앞뒤 공백을 제거한다")
        void stripsValues() {
            BusinessInfo info = BusinessInfo.of("  홍길동  ", "  211-88-79575  ", null, "  서울 성동구  ", null);

            assertThat(info.getRepresentativeName()).isEqualTo("홍길동");
            assertThat(info.getBusinessRegistrationNumber()).isEqualTo("211-88-79575");
            assertThat(info.getBusinessAddress()).isEqualTo("서울 성동구");
        }

        @Test
        @DisplayName("공백뿐인 값은 null로 정규화한다")
        void normalizesBlankToNull() {
            BusinessInfo info = BusinessInfo.of("홍길동", "211-88-79575", "   ", "서울 성동구", "  ");

            assertThat(info.getMailOrderSalesNumber()).isNull();
            assertThat(info.getBusinessEmail()).isNull();
        }
    }

    @Nested
    @DisplayName("표시의무 충족 여부 확인 시, ")
    class IsDisclosureComplete {

        @Test
        @DisplayName("필수 항목이 모두 있으면 true를 반환한다")
        void returnsTrueWhenRequiredPresent() {
            BusinessInfo info = BusinessInfo.of("홍길동", "211-88-79575", "2022-서울성동-01952", "서울 성동구", "owner@smile.kr");

            assertThat(info.isDisclosureComplete()).isTrue();
        }

        @Test
        @DisplayName("통신판매업 신고번호·이메일이 없어도 필수 항목이 있으면 true를 반환한다")
        void ignoresOptionalFields() {
            BusinessInfo info = BusinessInfo.of("홍길동", "211-88-79575", null, "서울 성동구", null);

            assertThat(info.isDisclosureComplete()).isTrue();
        }

        @Test
        @DisplayName("필수 항목이 하나라도 비면 false를 반환한다")
        void returnsFalseWhenRequiredMissing() {
            BusinessInfo missingAddress = BusinessInfo.of("홍길동", "211-88-79575", null, null, null);

            assertThat(missingAddress.isDisclosureComplete()).isFalse();
        }
    }
}