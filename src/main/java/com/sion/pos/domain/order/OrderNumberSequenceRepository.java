package com.sion.pos.domain.order;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderNumberSequenceRepository extends JpaRepository<OrderNumberSequence, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO order_number_sequence (
                store_id,
                order_date,
                last_order_number,
                created_at,
                updated_at
            )
            VALUES (:storeId, :orderDate, 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE updated_at = updated_at
            """, nativeQuery = true)
    void insertIfAbsent(@Param("storeId") Long storeId, @Param("orderDate") LocalDate orderDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderNumberSequence> findByStoreIdAndOrderDate(Long storeId, LocalDate orderDate);
}
