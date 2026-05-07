package com.sion.pos.application.store;

import com.sion.pos.domain.store.Store;
import com.sion.pos.domain.store.StoreRepository;
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
