package com.sion.pos.domain.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdInAndDeletedAtIsNullOrderByIdAsc(List<Long> orderIds);
}
