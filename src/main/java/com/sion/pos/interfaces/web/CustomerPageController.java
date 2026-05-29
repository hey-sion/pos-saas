package com.sion.pos.interfaces.web;

import com.sion.pos.application.store.StoreService;
import com.sion.pos.domain.store.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 손님 QR 셀프주문 화면 (공개 경로)
 */
@Controller
@RequiredArgsConstructor
public class CustomerPageController {

    private final StoreService storeService;

    @GetMapping("/order/{slug}")
    public String order(@PathVariable String slug, Model model) {
        Store store = storeService.getStoreBySlug(slug);
        model.addAttribute("storeId", store.getId());
        model.addAttribute("storeName", store.getName());
        return "customer/order";
    }
}
