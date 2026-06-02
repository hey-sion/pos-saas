package com.sion.pos.domain.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SupplyItemTest {

    @Nested
    @DisplayName("품목 카탈로그 값은, ")
    class CatalogValues {

        @Test
        @DisplayName("반죽믹스는 1포에 35,000원이다")
        void doughMixPricePerUnit() {
            assertThat(SupplyItem.DOUGH_MIX.getItemName()).isEqualTo("반죽믹스");
            assertThat(SupplyItem.DOUGH_MIX.getUnit()).isEqualTo("포");
            assertThat(SupplyItem.DOUGH_MIX.getUnitPrice()).isEqualTo(35_000);
            assertThat(SupplyItem.DOUGH_MIX.getPackSize()).isNull();
        }

        @Test
        @DisplayName("비닐봉투는 1묶음(6,000매)에 152,000원이다")
        void plasticBagPricePerPack() {
            assertThat(SupplyItem.PLASTIC_BAG.getItemName()).isEqualTo("비닐봉투");
            assertThat(SupplyItem.PLASTIC_BAG.getUnit()).isEqualTo("묶음");
            assertThat(SupplyItem.PLASTIC_BAG.getUnitPrice()).isEqualTo(152_000);
            assertThat(SupplyItem.PLASTIC_BAG.getPackSize()).isEqualTo(6_000);
            assertThat(SupplyItem.PLASTIC_BAG.getPackUnit()).isEqualTo("매");
        }
    }

    @Nested
    @DisplayName("itemCode로 품목을 조회할 때, ")
    class From {

        @Test
        @DisplayName("유효한 코드면 해당 품목을 반환한다")
        void returnsItemForValidCode() {
            assertThat(SupplyItem.from("DOUGH_MIX")).isEqualTo(SupplyItem.DOUGH_MIX);
            assertThat(SupplyItem.from("PLASTIC_BAG")).isEqualTo(SupplyItem.PLASTIC_BAG);
        }

        @Test
        @DisplayName("존재하지 않는 코드면 NOT_FOUND 예외를 발생시킨다")
        void throwsWhenCodeNotExists() {
            expects(ErrorType.NOT_FOUND, () -> SupplyItem.from("UNKNOWN"));
        }

        @Test
        @DisplayName("코드가 null 또는 공백이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenCodeBlank() {
            expects(ErrorType.BAD_REQUEST, () -> SupplyItem.from(null));
            expects(ErrorType.BAD_REQUEST, () -> SupplyItem.from(" "));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}