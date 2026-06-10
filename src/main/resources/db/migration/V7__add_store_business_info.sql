ALTER TABLE store
    ADD COLUMN representative_name          VARCHAR(50),
    ADD COLUMN business_registration_number VARCHAR(20),
    ADD COLUMN mail_order_sales_number      VARCHAR(50),
    ADD COLUMN business_address             VARCHAR(255),
    ADD COLUMN business_email               VARCHAR(100);