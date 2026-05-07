package com.sion.pos.menu.application;

import com.sion.pos.menu.domain.Menu;
import com.sion.pos.menu.domain.MenuRepository;
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