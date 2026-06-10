package com.sion.pos.domain.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StoreTest {

    @Nested
    @DisplayName("매장 생성 시, ")
    class Create {

        @Test
        @DisplayName("필수 값으로 매장을 생성한다")
        void createsStore() {
            Store store = Store.create("스마일카페", "02-1234-5678");

            assertThat(store.getName()).isEqualTo("스마일카페");
            assertThat(store.getPhone()).isEqualTo("02-1234-5678");
        }

        @Test
        @DisplayName("name이 null 또는 공백이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenNameIsBlank() {
            expects(ErrorType.BAD_REQUEST, () -> Store.create(null, "02-1234-5678"));
            expects(ErrorType.BAD_REQUEST, () -> Store.create("   ", "02-1234-5678"));
        }
    }

    @Nested
    @DisplayName("사업자 정보 변경 시, ")
    class UpdateBusinessInfo {

        @Test
        @DisplayName("사업자 정보를 설정한다")
        void updatesBusinessInfo() {
            Store store = Store.create("스마일카페", "02-1234-5678");
            BusinessInfo info = BusinessInfo.of("홍길동", "211-88-79575", null, "서울 성동구", null);

            store.updateBusinessInfo(info);

            assertThat(store.getBusinessInfo()).isSameAs(info);
            assertThat(store.getBusinessInfo().getRepresentativeName()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("null이면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenNull() {
            Store store = Store.create("스마일카페", "02-1234-5678");

            expects(ErrorType.BAD_REQUEST, () -> store.updateBusinessInfo(null));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}