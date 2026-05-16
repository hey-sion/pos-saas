CREATE TABLE order_number_sequence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    last_order_number INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_number_sequence_store_date (store_id, order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO order_number_sequence (
    store_id,
    order_date,
    last_order_number,
    created_at,
    updated_at
)
SELECT
    store_id,
    order_date,
    MAX(order_number),
    NOW(6),
    NOW(6)
FROM orders
GROUP BY store_id, order_date;
