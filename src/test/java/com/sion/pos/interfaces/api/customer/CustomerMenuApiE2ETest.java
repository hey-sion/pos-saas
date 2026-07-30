package com.sion.pos.interfaces.api.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
import com.sion.pos.interfaces.api.menu.MenuResponse;
import com.sion.pos.interfaces.api.order.OrderCreateRequest;
import com.sion.pos.support.DatabaseCleanUp;
import com.sion.pos.support.security.ApiTestClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerMenuApiE2ETest {

    @LocalServerPort private int port;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("손님이 QR로 매장 메뉴 조회 시, ")
    class GetMenus {

        @Test
        @DisplayName("로그인 없이 해당 매장의 메뉴를 정렬 순서대로 반환한다.")
        void returnsStoreMenusInSortOrder_withoutLogin() {
            Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            menuRepository.save(Menu.create(store.getId(), "아메리카노", 4_000, 1));
            menuRepository.save(Menu.create(store.getId(), "라떼", 5_000, 2));

            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).extracting(MenuResponse::name)
                                                        .containsExactly("아메리카노", "라떼"),
                    () -> assertThat(response.getBody()).extracting(MenuResponse::price)
                                                        .containsExactly(4_000, 5_000)
            );
        }

        @Test
        @DisplayName("다른 매장의 메뉴는 반환하지 않는다.")
        void doesNotReturnOtherStoreMenus() {
            Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            Store otherStore = storeRepository.save(Store.create("2번 테스트 매장", "010-2222-2222"));
            menuRepository.save(Menu.create(store.getId(), "내 매장 메뉴", 4_000, 1));
            menuRepository.save(Menu.create(otherStore.getId(), "다른 매장 메뉴", 9_000, 1));

            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).extracting(MenuResponse::name)
                                                        .containsExactly("내 매장 메뉴")
            );
        }

        @Test
        @DisplayName("한도가 없는 메뉴는 남은 수량을 내려주지 않는다.")
        void returnsNullRemainingQuantity_whenMenuHasNoDailyLimit() {
            // Arrange
            Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            menuRepository.save(Menu.create(store.getId(), "아메리카노", 4_000, 1));

            // Act
            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            // Assert
            assertThat(response.getBody()).extracting(MenuResponse::remainingQuantity)
                                          .containsExactly((Integer) null);
        }

        @Test
        @DisplayName("한정 메뉴가 오늘 아직 안 팔렸으면 한도 전체를 남은 수량으로 내려준다.")
        void returnsFullLimit_whenLimitedMenuNotSoldToday() {
            // Arrange
            Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            saveLimitedMenu(store.getId(), 10);

            // Act
            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            // Assert
            assertThat(response.getBody()).extracting(MenuResponse::remainingQuantity)
                                          .containsExactly(10);
        }

        @Test
        @DisplayName("한정 메뉴가 일부 팔렸으면 팔린 만큼 뺀 수량을 내려준다.")
        void returnsRemainingQuantity_whenLimitedMenuPartiallySold() {
            // Arrange
            Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            Long limitedMenuId = saveLimitedMenu(store.getId(), 10);
            order(store.getId(), limitedMenuId, 4);

            // Act
            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            // Assert
            assertThat(response.getBody()).extracting(MenuResponse::remainingQuantity)
                                          .containsExactly(6);
        }

        @Test
        @DisplayName("한정 메뉴가 다 팔렸으면 남은 수량 0을 내려준다.")
        void returnsZeroRemainingQuantity_whenLimitedMenuSoldOut() {
            // Arrange
            Store store = storeRepository.save(Store.create("1번 테스트 매장", "010-1111-1111"));
            Long limitedMenuId = saveLimitedMenu(store.getId(), 10);
            order(store.getId(), limitedMenuId, 10);

            // Act
            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            // Assert
            assertThat(response.getBody()).extracting(MenuResponse::remainingQuantity)
                                          .containsExactly(0);
        }

        @Test
        @DisplayName("메뉴가 없는 매장이면 빈 목록을 반환한다.")
        void returnsEmptyList_whenStoreHasNoMenus() {
            Store store = storeRepository.save(Store.create("메뉴 없는 매장", "010-0000-0000"));

            ResponseEntity<List<MenuResponse>> response = getMenus(store.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isEmpty()
            );
        }
    }

    private ResponseEntity<List<MenuResponse>> getMenus(Long storeId) {
        return ApiTestClient.plain(port).exchange(
                "/api/v1/customer/stores/" + storeId + "/menus",
                HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
    }

    private Long saveLimitedMenu(Long storeId, int dailyLimitQuantity) {
        Menu limited = Menu.create(storeId, "오늘의 디저트", 6_000, 1);
        limited.changeDailyLimitQuantity(dailyLimitQuantity);
        return menuRepository.save(limited).getId();
    }

    private void order(Long storeId, Long menuId, int quantity) {
        ApiTestClient.plain(port).exchange(
                "/api/v1/customer/stores/" + storeId + "/orders",
                HttpMethod.POST,
                new HttpEntity<>(new OrderCreateRequest(List.of(new OrderCreateRequest.Line(menuId, quantity)))),
                String.class);
    }
}