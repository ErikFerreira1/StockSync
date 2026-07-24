-- Remove the redundant order_id column (it duplicated origin_type/origin_id,
-- which already covers the generic reference to Order, manual adjustment, or reconciliation)
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS fk_stock_movements_order;
DROP INDEX IF EXISTS idx_stock_movements_order_id;
ALTER TABLE stock_movements DROP COLUMN IF EXISTS order_id;

-- Allow origin_type to be null: MANUAL_INCREASE/MANUAL_DECREASE have no origin
ALTER TABLE stock_movements ALTER COLUMN origin_type DROP NOT NULL;

-- Tighten the quantity constraint to match the business rule:
-- quantity is always positive, and MovementType determines the direction (increase or decrease)
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS ck_stock_movements_quantity_non_zero;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movements_quantity_positive CHECK (quantity > 0);