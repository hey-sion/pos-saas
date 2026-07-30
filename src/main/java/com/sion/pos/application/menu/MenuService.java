package com.sion.pos.application.menu;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuDailyStock;
import com.sion.pos.domain.menu.MenuDailyStockRepository;
import com.sion.pos.domain.menu.MenuRepository;
import com.sion.pos.support.time.BusinessTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuDailyStockRepository menuDailyStockRepository;

    @Transactional(readOnly = true)
    public List<MenuInfo> getActiveMenus(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId must not be null");
        }

        List<Menu> menus = menuRepository.findByStoreIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(storeId);
        Map<Long, MenuDailyStock> todayStockByMenuId = todayStockByMenuId(storeId, menus);

        return menus.stream()
                    .map(menu -> new MenuInfo(
                            menu.getId(),
                            menu.getName(),
                            menu.getPrice(),
                            menu.getSortOrder(),
                            remainingQuantity(menu, todayStockByMenuId.get(menu.getId()))))
                    .toList();
    }

    private Map<Long, MenuDailyStock> todayStockByMenuId(Long storeId, List<Menu> menus) {
        List<Long> limitedMenuIds = menus.stream()
                                         .filter(Menu::hasDailyLimit)
                                         .map(Menu::getId)
                                         .toList();
        if (limitedMenuIds.isEmpty()) {
            return Map.of();
        }

        LocalDate today = BusinessTime.today();
        return menuDailyStockRepository.findByStoreIdAndStockDateAndMenuIdInAndDeletedAtIsNull(storeId, today, limitedMenuIds)
                                       .stream()
                                       .collect(Collectors.toMap(MenuDailyStock::getMenuId, Function.identity()));
    }

    private Integer remainingQuantity(Menu menu, MenuDailyStock todayStock) {
        if (!menu.hasDailyLimit()) {
            return null;
        }
        if (todayStock == null) {
            return menu.getDailyLimitQuantity();
        }

        return todayStock.getLimitQuantity() - todayStock.getSoldQuantity();
    }
}