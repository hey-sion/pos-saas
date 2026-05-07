package com.sion.pos.application.menu;

import com.sion.pos.domain.menu.Menu;
import com.sion.pos.domain.menu.MenuRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    @Transactional(readOnly = true)
    public List<Menu> getActiveMenus(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId must not be null");
        }

        return menuRepository.findByStoreIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(storeId);
    }
}
