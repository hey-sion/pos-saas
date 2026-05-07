package com.sion.pos.interfaces.api.store;

import com.sion.pos.application.store.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreApiController {

    private final StoreService storeService;

    @GetMapping("/{storeId}")
    public StoreResponse getStore(@PathVariable Long storeId) {
        return StoreResponse.from(storeService.getStore(storeId));
    }
}
