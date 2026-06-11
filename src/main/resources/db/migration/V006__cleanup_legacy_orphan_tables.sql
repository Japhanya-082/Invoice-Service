-- =============================================================================
-- V006  Drop orphan / legacy / duplicate tables.
-- -----------------------------------------------------------------------------
-- Audit findings:
--   * roles_backup: 20 rows of stale role data in production.
--   * customer_info: empty orphan table.
--   * consultant_vendors: empty orphan (0 bytes).
--   * invoice / invoices: legacy invoice variants with 0 rows each.
--   * manual_invoice / manual_invoice_files: superseded by manual_invoices + invoice_items.
--   * manual_invoice_upload: superseded by manual_invoice_uploads with proper FKs.
--
-- Each drop is guarded with a row-count safety check — if non-empty,
-- the table is renamed to *_quarantine_<date> instead of deleted, so
-- ops can review before final removal.
-- =============================================================================

SET search_path TO invoice, public;

DO $$
DECLARE
    legacy TEXT;
    legacy_tables TEXT[] := ARRAY[
        'roles_backup', 'customer_info', 'consultant_vendors',
        'invoice', 'invoices', 'manual_invoice', 'manual_invoice_files'
    ];
    qty BIGINT;
    quarantine_suffix TEXT := to_char(NOW(), 'YYYYMMDD');
BEGIN
    FOREACH legacy IN ARRAY legacy_tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_schema='invoice' AND table_name=legacy) THEN
            EXECUTE format('SELECT COUNT(*) FROM invoice.%I', legacy) INTO qty;
            IF qty = 0 THEN
                EXECUTE format('DROP TABLE invoice.%I CASCADE', legacy);
                RAISE NOTICE 'Dropped empty legacy table: %', legacy;
            ELSE
                EXECUTE format(
                    'ALTER TABLE invoice.%I RENAME TO %I',
                    legacy, legacy || '_quarantine_' || quarantine_suffix);
                RAISE NOTICE 'Quarantined non-empty legacy table %: % rows', legacy, qty;
            END IF;
        END IF;
    END LOOP;
END $$;
