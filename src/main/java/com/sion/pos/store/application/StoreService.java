package com.sion.pos.store.application;

import com.sion.pos.store.domain.Store;
import com.sion.pos.store.domain.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public Store getStore(Long storeId) {
        return storeRepository.findById(storeId)
                              .orElseThrow(() -> new IllegalArgumentException("store not found"));
    }
}
