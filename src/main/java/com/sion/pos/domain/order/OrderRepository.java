package com.sion.pos.domain.order;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStoreIdAndOrderDateAndStatusOrderByOrderNumberAsc(
            Long storeId,
            LocalDate orderDate,
            Order.Status status
    );
}