package com.sion.pos.domain.order;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT MAX(o.orderNumber) FROM Order o "
            + "WHERE o.storeId = :storeId AND o.orderDate = :orderDate")
    Optional<Integer> findMaxOrderNumberByStoreIdAndOrderDate(
            @Param("storeId") Long storeId,
            @Param("orderDate") LocalDate orderDate);
}