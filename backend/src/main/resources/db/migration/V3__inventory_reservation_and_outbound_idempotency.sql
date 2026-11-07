ALTER TABLE wms_inventory
    ADD COLUMN reserved_qty INT NOT NULL DEFAULT 0 AFTER qty,
    ADD CONSTRAINT chk_inventory_reserved_nonnegative CHECK (reserved_qty >= 0),
    ADD CONSTRAINT chk_inventory_reserved_lte_qty CHECK (reserved_qty <= qty);

ALTER TABLE wms_outbound_item
    ADD COLUMN reserved_qty INT NOT NULL DEFAULT 0 AFTER picked_qty;

ALTER TABLE wms_outbound_order
    ADD COLUMN completion_key VARCHAR(64) NULL AFTER completed_at,
    ADD UNIQUE KEY uk_outbound_completion_key (completion_key);
