package com.sion.pos.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderItemTest {

    private static final Long ORDER_ID = 1L;
    private static final Long MENU_ID = 10L;
    private static final String MENU_NAME = "아메리카노";
    private static final Integer PRICE = 4_000;
    private static final Integer QUANTITY = 2;

    @Nested
    @DisplayName("주문 항목 생성 시, ")
    class Create {

        @Test
        @DisplayName("스냅샷된 메뉴명과 가격으로 주문 항목을 생성한다")
        void createsOrderItemWithSnapshot() {
            OrderItem item = OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, PRICE, QUANTITY);

            assertThat(item.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(item.getMenuId()).isEqualTo(MENU_ID);
            assertThat(item.getMenuName()).isEqualTo(MENU_NAME);
            assertThat(item.getPrice()).isEqualTo(PRICE);
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
        }

        @Test
        @DisplayName("orderId 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenOrderIdIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(null, MENU_ID, MENU_NAME, PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(0L, MENU_ID, MENU_NAME, PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(-1L, MENU_ID, MENU_NAME, PRICE, QUANTITY));
        }

        @Test
        @DisplayName("menuId 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMenuIdIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, null, MENU_NAME, PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, 0L, MENU_NAME, PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, -1L, MENU_NAME, PRICE, QUANTITY));
        }

        @Test
        @DisplayName("menuName 이 null 또는 공백이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenMenuNameIsBlank() {
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, null, PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, "", PRICE, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, "   ", PRICE, QUANTITY));
        }

        @Test
        @DisplayName("price 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenPriceIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, null, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, 0, QUANTITY));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, -1, QUANTITY));
        }

        @Test
        @DisplayName("quantity 가 null 또는 0 이하이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenQuantityIsNotPositive() {
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, PRICE, null));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, PRICE, 0));
            expects(ErrorType.BAD_REQUEST, () -> OrderItem.create(ORDER_ID, MENU_ID, MENU_NAME, PRICE, -1));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}
