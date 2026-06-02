package com.sion.pos.interfaces.api.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.application.store.StoreAccountService;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.domain.supply.SupplyOrder;
import com.sion.pos.domain.supply.SupplyOrderItem;
import com.sion.pos.domain.supply.SupplyOrderItemRepository;
import com.sion.pos.domain.supply.SupplyOrderRepository;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupplyOrderV1ApiE2ETest {

    private static final String ORDERS_ENDPOINT = "/api/v1/supply-orders";
    private static final String ITEMS_ENDPOINT = "/api/v1/supply-items";
    private static final int DOUGH_PRICE = 35_000;
    private static final int BAG_PRICE = 152_000;

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private SupplyOrderRepository supplyOrderRepository;
    @Autowired private SupplyOrderItemRepository supplyOrderItemRepository;
    @Autowired private StoreAccountService storeAccountService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private TestRestTemplate testRestTemplate;
    private Long storeId;

    @BeforeEach
    void setUp() {
        storeId = storeRepository.save(Store.create("1번 테스트 매장", "010-1234-5678")).getId();
        storeAccountService.register(storeId, "owner", "password1!");
        testRestTemplate = ApiTestClient.loggedIn(port, "owner", "password1!");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("발주 생성 시, ")
    class Create {

        @Test
        @DisplayName("유효한 요청이면 201 Created와 REQUESTED 상태의 발주를 반환하고, 서버 가격으로 스냅샷을 저장한다")
        void returnsCreated_whenValidRequest() {
            SupplyOrderCreateRequest request = new SupplyOrderCreateRequest(
                    List.of(new SupplyOrderCreateRequest.Line("DOUGH_MIX", 2),
                            new SupplyOrderCreateRequest.Line("PLASTIC_BAG", 1)));

            ResponseEntity<SupplyOrderCreateResponse> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), SupplyOrderCreateResponse.class);

            Long orderId = response.getBody().id();
            SupplyOrder order = supplyOrderRepository.findById(orderId).orElseThrow();
            List<SupplyOrderItem> items = supplyOrderItemRepository.findBySupplyOrderIdInAndDeletedAtIsNullOrderByIdAsc(List.of(orderId));
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().storeId()).isEqualTo(storeId),
                    () -> assertThat(response.getBody().status()).isEqualTo(SupplyOrder.Status.REQUESTED),
                    () -> assertThat(response.getBody().totalAmount()).isEqualTo(DOUGH_PRICE * 2 + BAG_PRICE),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(DOUGH_PRICE * 2 + BAG_PRICE),
                    () -> assertThat(items).hasSize(2)
            );
        }

        @Test
        @DisplayName("발주 항목이 비어 있으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenItemsEmpty() {
            SupplyOrderCreateRequest request = new SupplyOrderCreateRequest(List.of());

            ResponseEntity<Void> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("인증 없이 요청하면 401 또는 403으로 거부된다")
        void rejectsUnauthenticated() {
            TestRestTemplate plain = ApiTestClient.plain(port);
            SupplyOrderCreateRequest request = new SupplyOrderCreateRequest(
                    List.of(new SupplyOrderCreateRequest.Line("DOUGH_MIX", 1)));

            ResponseEntity<Void> response =
                    plain.exchange(ORDERS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), Void.class);

            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(supplyOrderRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("내 발주 조회 시, ")
    class GetOrders {

        @Test
        @DisplayName("우리 매장 발주만 최신순으로 반환한다")
        void returnsOwnOrdersInLatestOrder() {
            createOrder("DOUGH_MIX", 1);
            createOrder("PLASTIC_BAG", 1);
            createOtherStoreOrder();

            ResponseEntity<List<SupplyOrderResponse>> response =
                    testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});

            List<SupplyOrderResponse> body = response.getBody();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body).hasSize(2),
                    () -> assertThat(body.get(0).items()).extracting(SupplyOrderResponse.Item::itemCode).containsExactly("PLASTIC_BAG"),
                    () -> assertThat(body.get(0).items()).extracting(SupplyOrderResponse.Item::itemName).containsExactly("비닐봉투"),
                    () -> assertThat(body.get(1).items()).extracting(SupplyOrderResponse.Item::itemCode).containsExactly("DOUGH_MIX"),
                    () -> assertThat(body).allSatisfy(order -> assertThat(order.status()).isEqualTo("REQUESTED"))
            );
        }
    }

    @Nested
    @DisplayName("품목 카탈로그 조회 시, ")
    class GetItems {

        @Test
        @DisplayName("반죽믹스/비닐봉투 품목과 서버 가격을 반환한다")
        void returnsCatalogWithPrices() {
            ResponseEntity<List<SupplyItemResponse>> response =
                    testRestTemplate.exchange(ITEMS_ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});

            List<SupplyItemResponse> body = response.getBody();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body).extracting(SupplyItemResponse::code).containsExactlyInAnyOrder("DOUGH_MIX", "PLASTIC_BAG"),
                    () -> assertThat(body).anySatisfy(item -> {
                        assertThat(item.code()).isEqualTo("DOUGH_MIX");
                        assertThat(item.name()).isEqualTo("반죽믹스");
                        assertThat(item.unit()).isEqualTo("포");
                        assertThat(item.unitPrice()).isEqualTo(DOUGH_PRICE);
                    }),
                    () -> assertThat(body).anySatisfy(item -> {
                        assertThat(item.code()).isEqualTo("PLASTIC_BAG");
                        assertThat(item.unitPrice()).isEqualTo(BAG_PRICE);
                        assertThat(item.packSize()).isEqualTo(6_000);
                        assertThat(item.packUnit()).isEqualTo("매");
                    })
            );
        }
    }

    private void createOrder(String itemCode, int quantity) {
        SupplyOrderCreateRequest request = new SupplyOrderCreateRequest(
                List.of(new SupplyOrderCreateRequest.Line(itemCode, quantity)));
        ResponseEntity<SupplyOrderCreateResponse> response =
                testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), SupplyOrderCreateResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void createOtherStoreOrder() {
        Long otherStoreId = storeRepository.save(Store.create("2번 테스트 매장", "010-9999-9999")).getId();
        SupplyOrder order = supplyOrderRepository.save(SupplyOrder.create(otherStoreId, DOUGH_PRICE));
        supplyOrderItemRepository.save(SupplyOrderItem.create(order.getId(), "DOUGH_MIX", "반죽믹스", "포", DOUGH_PRICE, 1));
    }
}