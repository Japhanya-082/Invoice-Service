-- =============================================================================
-- V005  Audit columns, optimistic-locking version, soft-delete.
-- =============================================================================

SET search_path TO invoice, public;

DO $$
DECLARE
    tbl TEXT;
    tables TEXT[] := ARRAY['manual_invoices','invoice_items','vendor_info','consultant',
                           'employments','contributions','manage_users','roles','privileges'];
BEGIN
    FOREACH tbl IN ARRAY tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_schema='invoice' AND table_name=tbl) THEN
            EXECUTE format('ALTER TABLE invoice.%I ADD COLUMN IF NOT EXISTS created_by BIGINT', tbl);
            EXECUTE format('ALTER TABLE invoice.%I ADD COLUMN IF NOT EXISTS updated_by BIGINT', tbl);
            EXECUTE format('ALTER TABLE invoice.%I ADD COLUMN IF NOT EXISTS version    BIGINT NOT NULL DEFAULT 0', tbl);
            EXECUTE format('ALTER TABLE invoice.%I ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP', tbl);
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON invoice.%I (deleted_at) WHERE deleted_at IS NULL',
                           'idx_'||tbl||'_active', tbl);
        END IF;
    END LOOP;
END $$;
