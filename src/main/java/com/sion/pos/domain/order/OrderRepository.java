package com.sion.pos.domain.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStoreIdAndOrderDateAndStatusOrderByOrderNumberAsc(
            Long storeId,
            LocalDate orderDate,
            Order.Status status
    );

    List<Order> findByStoreIdAndOrderDateAndStatusInOrderByOrderNumberAsc(
            Long storeId,
            LocalDate orderDate,
            List<Order.Status> statuses
    );

    Optional<Order> findByIdAndStoreId(Long id, Long storeId);
}
