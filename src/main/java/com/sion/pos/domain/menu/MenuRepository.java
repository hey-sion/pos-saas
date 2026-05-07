package com.sion.pos.domain.menu;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByStoreIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long storeId);
}
