package com.sion.pos.interfaces.web;

import com.sion.pos.application.store.StoreService;
import com.sion.pos.domain.store.Store;
import com.sion.pos.support.error.ErrorType;
import com.sion.pos.support.error.PosApplicationException;
import java.util.Map;
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

    /** 공개 정책 타입 화이트리스트 (path traversal 방지 + 템플릿 분기). */
    private static final Map<String, String> POLICY_TITLES = Map.of(
            "terms", "이용약관",
            "privacy", "개인정보처리방침",
            "refund", "취소·환불 정책"
    );

    private final StoreService storeService;

    @GetMapping("/order/{slug}")
    public String order(@PathVariable String slug, Model model) {
        Store store = storeService.getStoreBySlug(slug);
        model.addAttribute("storeId", store.getId());
        model.addAttribute("storeName", store.getName());
        model.addAttribute("storePhone", store.getPhone());
        model.addAttribute("businessInfo", store.getBusinessInfo());
        model.addAttribute("slug", slug);
        return "customer/order";
    }

    @GetMapping("/order/{slug}/policy/{type}")
    public String policy(@PathVariable String slug, @PathVariable String type, Model model) {
        String title = POLICY_TITLES.get(type);
        if (title == null) {
            throw new PosApplicationException(ErrorType.NOT_FOUND, "존재하지 않는 정책입니다.");
        }

        Store store = storeService.getStoreBySlug(slug);
        model.addAttribute("storeName", store.getName());
        model.addAttribute("storePhone", store.getPhone());
        model.addAttribute("businessInfo", store.getBusinessInfo());
        model.addAttribute("slug", slug);
        model.addAttribute("policyType", type);
        model.addAttribute("policyTitle", title);
        return "customer/policy";
    }
}