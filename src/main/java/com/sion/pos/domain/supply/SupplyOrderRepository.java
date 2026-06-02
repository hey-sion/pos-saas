package com.sion.pos.domain.supply;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyOrderRepository extends JpaRepository<SupplyOrder, Long> {

    List<SupplyOrder> findByStoreIdAndDeletedAtIsNullOrderByIdDesc(Long storeId);

    Optional<SupplyOrder> findByIdAndStoreId(Long id, Long storeId);
}