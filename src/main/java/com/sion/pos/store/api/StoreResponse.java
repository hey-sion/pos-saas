package com.sion.pos.store.api;

import com.sion.pos.store.domain.Store;

public record StoreResponse(
        Long id,
        String name
) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(store.getId(), store.getName());
    }
}
