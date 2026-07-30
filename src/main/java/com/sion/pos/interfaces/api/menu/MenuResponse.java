package com.sion.pos.interfaces.api.menu;

import com.sion.pos.application.menu.MenuInfo;

public record MenuResponse(
        Long id,
        String name,
        Integer price,
        Integer sortOrder,
        Integer remainingQuantity
) {

    public static MenuResponse from(MenuInfo menu) {
        return new MenuResponse(
                menu.id(),
                menu.name(),
                menu.price(),
                menu.sortOrder(),
                menu.remainingQuantity()
        );
    }
}