package com.sion.pos.application.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.domain.supply.SupplyOrder;
import com.sion.pos.domain.supply.SupplyOrderItem;
import com.sion.pos.domain.supply.SupplyOrderItemRepository;
import com.sion.pos.domain.supply.SupplyOrderRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SupplyOrderFacadeIntegrationTest {

    @Autowired private SupplyOrderFacade supplyOrderFacade;
    @Autowired private SupplyOrderService supplyOrderService;
    @Autowired private SupplyOrderRepository supplyOrderRepository;
    @Autowired private SupplyOrderItemRepository supplyOrderItemRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final int DOUGH_PRICE = 35_000;
    private static final int BAG_PRICE = 152_000;

    private Long storeId;

    @BeforeEach
    void setUp() {
        storeId = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("발주 생성 시, ")
    class CreateOrder {

        @Test
        @DisplayName("발주는 REQUESTED 상태로 저장되고, 총액은 서버 카탈로그 가격으로 계산된다")
        void persistsOrderWithServerCalculatedTotalAmount() {
            SupplyOrderCreateCommand command = new SupplyOrderCreateCommand(
                    storeId,
                    List.of(new SupplyOrderLine("DOUGH_MIX", 2),
                            new SupplyOrderLine("PLASTIC_BAG", 1)));

            SupplyOrder created = supplyOrderFacade.createOrder(command);

            SupplyOrder persisted = supplyOrderRepository.findById(created.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(SupplyOrder.Status.REQUESTED);
            assertThat(persisted.getStoreId()).isEqualTo(storeId);
            assertThat(persisted.getTotalAmount()).isEqualTo(DOUGH_PRICE * 2 + BAG_PRICE);
        }

        @Test
        @DisplayName("발주 항목에 주문 시점의 품목명/단위/가격 스냅샷이 저장된다")
        void persistsItemsWithCatalogSnapshot() {
            SupplyOrderCreateCommand command = new SupplyOrderCreateCommand(
                    storeId,
                    List.of(new SupplyOrderLine("DOUGH_MIX", 2),
                            new SupplyOrderLine("PLASTIC_BAG", 1)));

            SupplyOrder created = supplyOrderFacade.createOrder(command);

            List<SupplyOrderItem> items = supplyOrderItemRepository
                    .findBySupplyOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(created.getId()));
            assertThat(items).hasSize(2);
            assertThat(items).anySatisfy(item -> {
                assertThat(item.getItemCode()).isEqualTo("DOUGH_MIX");
                assertThat(item.getItemName()).isEqualTo("반죽믹스");
                assertThat(item.getUnit()).isEqualTo("포");
                assertThat(item.getUnitPrice()).isEqualTo(DOUGH_PRICE);
                assertThat(item.getQuantity()).isEqualTo(2);
            });
            assertThat(items).anySatisfy(item -> {
                assertThat(item.getItemCode()).isEqualTo("PLASTIC_BAG");
                assertThat(item.getItemName()).isEqualTo("비닐봉투");
                assertThat(item.getUnit()).isEqualTo("묶음");
                assertThat(item.getUnitPrice()).isEqualTo(BAG_PRICE);
                assertThat(item.getQuantity()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("발주 항목이 비어 있으면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenItemsEmpty() {
            SupplyOrderCreateCommand command = new SupplyOrderCreateCommand(storeId, List.of());

            expects(ErrorType.BAD_REQUEST, () -> supplyOrderFacade.createOrder(command));
        }

        @Test
        @DisplayName("중복된 품목이 포함되면 BAD_REQUEST 예외를 발생시킨다")
        void throwsWhenDuplicatedItem() {
            SupplyOrderCreateCommand command = new SupplyOrderCreateCommand(
                    storeId,
                    List.of(new SupplyOrderLine("DOUGH_MIX", 1),
                            new SupplyOrderLine("DOUGH_MIX", 2)));

            expects(ErrorType.BAD_REQUEST, () -> supplyOrderFacade.createOrder(command));
        }

        @Test
        @DisplayName("존재하지 않는 품목 코드가 포함되면 NOT_FOUND 예외를 발생시킨다")
        void throwsWhenItemCodeNotExists() {
            SupplyOrderCreateCommand command = new SupplyOrderCreateCommand(
                    storeId,
                    List.of(new SupplyOrderLine("UNKNOWN", 1)));

            expects(ErrorType.NOT_FOUND, () -> supplyOrderFacade.createOrder(command));
        }
    }

    @Nested
    @DisplayName("내 발주 조회 시, ")
    class GetSupplyOrders {

        @Test
        @DisplayName("우리 매장 발주를 최신순으로 반환하고, 다른 매장 발주는 제외한다")
        void returnsOwnOrdersInLatestOrder() {
            supplyOrderFacade.createOrder(new SupplyOrderCreateCommand(storeId, List.of(new SupplyOrderLine("DOUGH_MIX", 1))));
            supplyOrderFacade.createOrder(new SupplyOrderCreateCommand(storeId, List.of(new SupplyOrderLine("PLASTIC_BAG", 1))));
            Long otherStoreId = storeRepository.save(Store.create("다른 매장", null)).getId();
            supplyOrderFacade.createOrder(new SupplyOrderCreateCommand(otherStoreId, List.of(new SupplyOrderLine("DOUGH_MIX", 1))));

            List<SupplyOrderInfo> infos = supplyOrderService.getSupplyOrders(storeId);

            assertThat(infos).hasSize(2);
            assertThat(infos.get(0).items()).extracting(SupplyOrderInfo.Item::itemCode).containsExactly("PLASTIC_BAG");
            assertThat(infos.get(1).items()).extracting(SupplyOrderInfo.Item::itemCode).containsExactly("DOUGH_MIX");
            assertThat(infos).allSatisfy(info -> assertThat(info.status()).isEqualTo("REQUESTED"));
        }
    }

    private static void expects(ErrorType expected, ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(PosApplicationException.class,
                                    e -> assertThat(e.getErrorType()).isEqualTo(expected));
    }
}