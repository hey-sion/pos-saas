package com.sion.pos.menu.api;

import com.sion.pos.menu.domain.Menu;

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