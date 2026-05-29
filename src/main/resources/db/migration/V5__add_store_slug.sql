ALTER TABLE store ADD COLUMN slug VARCHAR(30);

ALTER TABLE store ADD UNIQUE KEY uk_store_slug (slug);