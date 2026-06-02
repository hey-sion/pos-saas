package com.sion.pos.domain.supply;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyOrderItemRepository extends JpaRepository<SupplyOrderItem, Long> {

    List<SupplyOrderItem> findBySupplyOrderIdInAndDeletedAtIsNullOrderByIdAsc(List<Long> supplyOrderIds);
}