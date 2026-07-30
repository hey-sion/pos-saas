-- 한정 수량 판매. daily_limit_quantity 가 NULL 인 메뉴는 수량 제한 없음
ALTER TABLE menu ADD COLUMN daily_limit_quantity INT NULL AFTER price;

-- 그날 팔린 수량. 한정 메뉴에 첫 주문이 들어올 때 로우 생성
CREATE TABLE menu_daily_stock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    stock_date DATE NOT NULL,
    limit_quantity INT NOT NULL,
    sold_quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_daily_stock_menu_date (menu_id, stock_date),
    KEY idx_menu_daily_stock_store_date (store_id, stock_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;