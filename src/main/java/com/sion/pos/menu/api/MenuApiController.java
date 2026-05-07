package com.sion.pos.menu.api;

import com.sion.pos.menu.application.MenuService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuApiController {

    private final MenuService menuService;

    @GetMapping
    public List<MenuResponse> getMenus(@RequestParam Long storeId) {
        return menuService.getActiveMenus(storeId).stream()
                          .map(MenuResponse::from)
                          .toList();
    }
}