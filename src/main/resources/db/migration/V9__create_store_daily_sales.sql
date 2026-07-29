CREATE TABLE store_daily_sales (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    sales_date DATE NOT NULL,
    sales_amount BIGINT NOT NULL,
    order_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_daily_sales_store_date (store_id, sales_date),
    KEY idx_store_daily_sales_updated_at (updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;