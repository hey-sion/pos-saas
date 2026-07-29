-- 집계 테이블 생성 시점 전에 생성된 데이터들 백필
INSERT IGNORE INTO store_daily_sales (
    store_id, sales_date, sales_amount, order_count, created_at, updated_at
)
SELECT store_id,
       order_date,
       SUM(total_amount),
       COUNT(*),
       NOW(6),
       NOW(6)
FROM orders
WHERE status = 'DELIVERED'
  AND deleted_at IS NULL
GROUP BY store_id, order_date;