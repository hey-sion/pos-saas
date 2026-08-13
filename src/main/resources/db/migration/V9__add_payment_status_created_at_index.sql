ALTER TABLE payment
    ADD INDEX idx_payment_status_created_at (status, created_at)