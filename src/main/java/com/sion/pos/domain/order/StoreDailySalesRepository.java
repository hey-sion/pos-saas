package com.sion.pos.domain.order;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreDailySalesRepository extends JpaRepository<StoreDailySales, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO store_daily_sales (
                store_id, sales_date, sales_amount, order_count, created_at, updated_at
            )
            VALUES (:storeId, :salesDate, :amount, 1, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                sales_amount = sales_amount + :amount,
                order_count = order_count + 1,
                updated_at = NOW(6)
            """, nativeQuery = true)
    void addAmount(@Param("storeId") Long storeId,
                   @Param("salesDate") LocalDate salesDate,
                   @Param("amount") int amount);

    Optional<StoreDailySales> findByStoreIdAndSalesDate(Long storeId, LocalDate salesDate);
}