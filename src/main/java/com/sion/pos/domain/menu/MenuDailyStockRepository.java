package com.sion.pos.domain.menu;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuDailyStockRepository extends JpaRepository<MenuDailyStock, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO menu_daily_stock (
                store_id, menu_id, stock_date, limit_quantity, sold_quantity, created_at, updated_at
            )
            VALUES (:storeId, :menuId, :stockDate, :limitQuantity, 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE updated_at = updated_at
            """, nativeQuery = true)
    void insertIfAbsent(@Param("storeId") Long storeId,
                        @Param("menuId") Long menuId,
                        @Param("stockDate") LocalDate stockDate,
                        @Param("limitQuantity") int limitQuantity);

    @Modifying
    @Query(value = """
            UPDATE menu_daily_stock
               SET sold_quantity = sold_quantity + :quantity,
                   updated_at = CURRENT_TIMESTAMP(6)
             WHERE store_id = :storeId
               AND menu_id = :menuId
               AND stock_date = :stockDate
               AND deleted_at IS NULL
               AND sold_quantity + :quantity <= limit_quantity
            """, nativeQuery = true)
    int deduct(@Param("storeId") Long storeId,
               @Param("menuId") Long menuId,
               @Param("stockDate") LocalDate stockDate,
               @Param("quantity") int quantity);

    @Modifying
    @Query(value = """
            UPDATE menu_daily_stock
               SET sold_quantity = sold_quantity - :quantity,
                   updated_at = CURRENT_TIMESTAMP(6)
             WHERE store_id = :storeId
               AND menu_id = :menuId
               AND stock_date = :stockDate
               AND deleted_at IS NULL
               AND sold_quantity >= :quantity
            """, nativeQuery = true)
    int restore(@Param("storeId") Long storeId,
                @Param("menuId") Long menuId,
                @Param("stockDate") LocalDate stockDate,
                @Param("quantity") int quantity);

    Optional<MenuDailyStock> findByStoreIdAndMenuIdAndStockDateAndDeletedAtIsNull(Long storeId,
                                                                                 Long menuId,
                                                                                 LocalDate stockDate);

    List<MenuDailyStock> findByStoreIdAndStockDateAndMenuIdInAndDeletedAtIsNull(Long storeId,
                                                                               LocalDate stockDate,
                                                                               Collection<Long> menuIds);
}