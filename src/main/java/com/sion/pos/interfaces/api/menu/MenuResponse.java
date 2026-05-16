package com.sion.pos.interfaces.api.menu;

import com.sion.pos.domain.menu.Menu;

public record MenuResponse(
        Long id,
        String name,
        Integer price,
        Integer sortOrder
) {

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getPrice(),
                menu.getSortOrder()
        );
    }
}
