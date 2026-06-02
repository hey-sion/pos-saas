package com.sion.pos.domain.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SupplyOrderItemTest {

    private static final Long SUPPLY_ORDER_ID = 1L;
    private static final String ITEM_CODE = "DOUGH_MIX";
    private static final String ITEM_NAME = "반죽믹스";
    private static final String UNIT = "포";
    private static final Integer UNIT_PRICE = 35_000;
    private static final Integer QUANTITY = 3;

    @Nested
    @DisplayName("발주 항목 생성 시, ")
    class Create {

        @Test
        @DisplayName("주문 시점의 품목명/단위/가격 스냅샷과 수량을 보존한다")
        void createsItemWithSnapshot() {
            SupplyOrderItem item = SupplyOrderItem.create(SUPPLY_ORDER_ID, ITEM_CODE, ITEM_NAME, UNIT, UNIT_PRICE, QUANTITY);

            assertThat(item.getSupplyOrderId()).isEqualTo(SUPPLY_ORDER_ID);
            assertThat(item.getItemCode()).isEqualTo(ITEM_CODE);
            assertThat(item.getItemName()).isEqualTo(ITEM_NAME);
            assertThat(item.getUnit()).isEqualTo(UNIT);
            assertThat(item.getUnitPrice()).isEqualTo(UNIT_PRICE);
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
        }

        @Test
        @DisplayName("supplyOrderId가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenSupplyOrderIdIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(null, ITEM_CODE, ITEM_NAME, UNIT, UNIT_PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(0L, ITEM_CODE, ITEM_NAME, UNIT, UNIT_PRICE, QUANTITY));
        }

        @Test
        @DisplayName("itemCode/itemName/unit이 비어 있으면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenTextFieldsBlank() {
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(SUPPLY_ORDER_ID, " ", ITEM_NAME, UNIT, UNIT_PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(SUPPLY_ORDER_ID, ITEM_CODE, " ", UNIT, UNIT_PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(SUPPLY_ORDER_ID, ITEM_CODE, ITEM_NAME, " ", UNIT_PRICE, QUANTITY));
        }

        @Test
        @DisplayName("unitPrice/quantity가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenNumbersNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(SUPPLY_ORDER_ID, ITEM_CODE, ITEM_NAME, UNIT, 0, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(SUPPLY_ORDER_ID, ITEM_CODE, ITEM_NAME, UNIT, UNIT_PRICE, 0));
            expects(ErrorType.BAD_REQUEST, () -> SupplyOrderItem.create(SUPPLY_ORDER_ID, ITEM_CODE, ITEM_NAME, UNIT, UNIT_PRICE, null));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}