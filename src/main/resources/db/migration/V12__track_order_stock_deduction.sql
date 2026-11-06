ALTER TABLE orders ADD COLUMN stock_deducted BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve historical deductions without changing balances or inventing payments.
UPDATE orders o SET stock_deducted = TRUE
WHERE EXISTS (
    SELECT 1 FROM stock_movements m
    WHERE m.origin_type = 'ORDER' AND m.origin_id = o.id AND m.type = 'SALE'
) AND NOT EXISTS (
    SELECT 1 FROM stock_movements m
    WHERE m.origin_type = 'ORDER' AND m.origin_id = o.id AND m.type = 'CANCELLATION'
);
