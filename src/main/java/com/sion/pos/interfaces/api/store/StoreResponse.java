package com.sion.pos.interfaces.api.store;

import com.sion.pos.domain.store.Store;

public record StoreResponse(
        Long id,
        String name
) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(store.getId(), store.getName());
    }
}
