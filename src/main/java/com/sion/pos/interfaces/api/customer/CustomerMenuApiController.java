package com.sion.pos.interfaces.api.customer;

import com.sion.pos.application.menu.MenuService;
import com.sion.pos.interfaces.api.menu.MenuResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 손님 QR 셀프주문용 공개 메뉴 조회. 세션이 없으므로 storeId를 URL path로 받는다(사장님 경로는 @LoginStore 세션).
 */
@RestController
@RequestMapping("/api/v1/customer/stores/{storeId}/menus")
@RequiredArgsConstructor
public class CustomerMenuApiController {

    private final MenuService menuService;

    @GetMapping
    public List<MenuResponse> getMenus(@PathVariable Long storeId) {
        return menuService.getActiveMenus(storeId).stream()
                          .map(MenuResponse::from)
                          .toList();
    }
}