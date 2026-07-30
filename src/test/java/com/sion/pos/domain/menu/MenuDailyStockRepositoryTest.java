package com.sion.pos.domain.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MenuDailyStockRepositoryTest {

    private static final Long STORE_ID = 1L;
    private static final Long OTHER_STORE_ID = 2L;
    private static final Long MENU_ID = 10L;
    private static final LocalDate STOCK_DATE = LocalDate.of(2026, 7, 30);
    private static final int LIMIT_QUANTITY = 10;

    @Autowired private MenuDailyStockRepository menuDailyStockRepository;

    @Nested
    @DisplayName("그날 재고 행을 만들 때, ")
    class InsertIfAbsent {

        @Test
        @DisplayName("메뉴 한도가 복사되고 판매 수량은 0으로 시작한다")
        void copiesLimitAndStartsFromZero() {
            // Arrange & Act
            menuDailyStockRepository.insertIfAbsent(STORE_ID, MENU_ID, STOCK_DATE, LIMIT_QUANTITY);

            // Assert
            assertThat(todayStock().getLimitQuantity()).isEqualTo(LIMIT_QUANTITY);
            assertThat(todayStock().getSoldQuantity()).isZero();
        }

        @Test
        @DisplayName("이미 행이 있으면 한도를 덮어쓰지 않는다")
        void doesNotOverwriteLimit() {
            // Arrange
            givenTodayStock();

            // Act
            menuDailyStockRepository.insertIfAbsent(STORE_ID, MENU_ID, STOCK_DATE, 999);

            // Assert
            assertThat(todayStock().getLimitQuantity()).isEqualTo(LIMIT_QUANTITY);
        }

        @Test
        @DisplayName("이미 팔린 수량이 있으면 0으로 되돌리지 않는다")
        void doesNotResetSoldQuantity() {
            // Arrange
            givenTodayStock();
            menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 4);

            // Act
            menuDailyStockRepository.insertIfAbsent(STORE_ID, MENU_ID, STOCK_DATE, LIMIT_QUANTITY);

            // Assert
            assertThat(soldQuantity()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("재고를 차감할 때, ")
    class Reserve {

        @BeforeEach
        void setUp() {
            givenTodayStock();
        }

        @Test
        @DisplayName("남은 수량 안에서 요청하면 판매 수량이 늘어난다")
        void increasesSoldQuantityWithinLimit() {
            // Arrange & Act
            int deducted = menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 3);

            // Assert
            assertThat(deducted).isEqualTo(1);
            assertThat(soldQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("남은 수량과 정확히 같은 수량은 차감된다")
        void deductsExactlyRemainingQuantity() {
            // Arrange
            menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 7);

            // Act
            int deducted = menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 3);

            // Assert
            assertThat(deducted).isEqualTo(1);
            assertThat(soldQuantity()).isEqualTo(LIMIT_QUANTITY);
        }

        @Test
        @DisplayName("한도를 넘기는 수량은 차감되지 않고 판매 수량도 그대로다")
        void rejectsQuantityOverLimit() {
            // Arrange
            menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 9);

            // Act
            int deducted = menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 2);

            // Assert
            assertThat(deducted).isZero();
            assertThat(soldQuantity()).isEqualTo(9);
        }

        @Test
        @DisplayName("다른 매장 ID로는 차감되지 않는다")
        void rejectsOtherStore() {
            // Arrange & Act
            int deducted = menuDailyStockRepository.deduct(OTHER_STORE_ID, MENU_ID, STOCK_DATE, 1);

            // Assert
            assertThat(deducted).isZero();
            assertThat(soldQuantity()).isZero();
        }

        @Test
        @DisplayName("다른 날짜에는 차감되지 않는다")
        void rejectsOtherDate() {
            // Arrange & Act
            int deducted = menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE.plusDays(1), 1);

            // Assert
            assertThat(deducted).isZero();
            assertThat(soldQuantity()).isZero();
        }
    }

    @Nested
    @DisplayName("재고를 되돌릴 때, ")
    class Release {

        @BeforeEach
        void setUp() {
            givenTodayStock();
        }

        @Test
        @DisplayName("복구한 만큼 다시 차감할 수 있다")
        void restoresReservedQuantity() {
            // Arrange
            menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, LIMIT_QUANTITY);

            // Act
            int restored = menuDailyStockRepository.restore(STORE_ID, MENU_ID, STOCK_DATE, 4);

            // Assert
            assertThat(restored).isEqualTo(1);
            assertThat(soldQuantity()).isEqualTo(6);
            assertThat(menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 4)).isEqualTo(1);
        }

        @Test
        @DisplayName("판매된 수량보다 많이 되돌릴 수는 없다")
        void rejectsReleaseMoreThanSold() {
            // Arrange
            menuDailyStockRepository.deduct(STORE_ID, MENU_ID, STOCK_DATE, 2);

            // Act
            int restored = menuDailyStockRepository.restore(STORE_ID, MENU_ID, STOCK_DATE, 3);

            // Assert
            assertThat(restored).isZero();
            assertThat(soldQuantity()).isEqualTo(2);
        }
    }

    private void givenTodayStock() {
        menuDailyStockRepository.insertIfAbsent(STORE_ID, MENU_ID, STOCK_DATE, LIMIT_QUANTITY);
    }

    private int soldQuantity() {
        return todayStock().getSoldQuantity();
    }

    private MenuDailyStock todayStock() {
        return menuDailyStockRepository.findByStoreIdAndMenuIdAndStockDateAndDeletedAtIsNull(STORE_ID, MENU_ID, STOCK_DATE)
                                       .orElseThrow();
    }
}