-- =============================================================================
-- V001  Foreign-key, tenant, and search indexes; vendor_id type correction.
-- -----------------------------------------------------------------------------
-- Run order: first migration after baseline. Idempotent — every CREATE INDEX
-- uses IF NOT EXISTS and the manual_invoices.vendor_id type change is guarded
-- with an information_schema check.
-- =============================================================================

SET search_path TO invoice, public;

-- Tenant + status access patterns on manual_invoices ------------------------------
CREATE INDEX IF NOT EXISTS idx_manual_invoices_admin_status        ON manual_invoices (admin_id, status);
CREATE INDEX IF NOT EXISTS idx_manual_invoices_admin_due_date      ON manual_invoices (admin_id, due_date);
CREATE INDEX IF NOT EXISTS idx_manual_invoices_admin_invoice_date  ON manual_invoices (admin_id, invoice_date);
CREATE INDEX IF NOT EXISTS idx_manual_invoices_admin_vendor        ON manual_invoices (admin_id, vendor_type);
CREATE INDEX IF NOT EXISTS idx_manual_invoices_consultant          ON manual_invoices (consultant_id);
CREATE INDEX IF NOT EXISTS idx_manual_invoices_employment          ON manual_invoices (employment_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_manual_invoices_admin_invnum  ON manual_invoices (admin_id, invoice_number);
CREATE UNIQUE INDEX IF NOT EXISTS uq_manual_invoices_admin_po      ON manual_invoices (admin_id, po_number)
    WHERE po_number IS NOT NULL AND po_number <> '';

CREATE INDEX IF NOT EXISTS idx_invoice_items_manual_invoice        ON invoice_items (manual_invoice_id);

-- Vendor + consultant tenant access ----------------------------------------------
CREATE INDEX IF NOT EXISTS idx_vendor_admin                        ON vendor_info (admin_id);
CREATE INDEX IF NOT EXISTS idx_consultant_admin                    ON consultant (admin_id);
CREATE INDEX IF NOT EXISTS idx_employments_admin                   ON employments (admin_id);
CREATE INDEX IF NOT EXISTS idx_consultant_email_lower              ON consultant (LOWER(email));
CREATE INDEX IF NOT EXISTS idx_consultant_cid                      ON consultant (cid);

-- Reporting / fraud / AI logs ----------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_tenant_status          ON fraud_alerts (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_detected_at            ON fraud_alerts (detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_recon_tenant_status                 ON reconciliation_records (tenant_id, match_status);
CREATE INDEX IF NOT EXISTS idx_monthly_summary_tenant_month        ON monthly_invoice_summary (tenant_id, year_month);
CREATE INDEX IF NOT EXISTS idx_ai_query_logs_tenant_user           ON ai_query_logs (tenant_id, user_id, created_at DESC);

-- Authentication, role, privilege -----------------------------------------------
CREATE INDEX IF NOT EXISTS idx_manage_users_admin                  ON manage_users (admin_id);
CREATE INDEX IF NOT EXISTS idx_manage_users_email_lower            ON manage_users (LOWER(email));
CREATE INDEX IF NOT EXISTS idx_role_admin                          ON roles (admin_id);
CREATE INDEX IF NOT EXISTS idx_privileges_admin                    ON privileges (admin_id);

-- ---------------------------------------------------------------------------------
-- manual_invoices.vendor_id type correction.
-- The audit found this column stored as varchar, breaking joins against
-- vendor_info(vendor_id BIGINT). Convert in place; rows with non-numeric
-- vendor strings are nulled (logged for back-office reconciliation).
-- ---------------------------------------------------------------------------------
DO $$
DECLARE
    col_type text;
BEGIN
    SELECT data_type INTO col_type
      FROM information_schema.columns
     WHERE table_schema = 'invoice'
       AND table_name   = 'manual_invoices'
       AND column_name  = 'vendor_id';

    IF col_type IS NOT NULL AND col_type IN ('character varying', 'text') THEN
        -- Backfill table for non-numeric values, then strip them.
        CREATE TABLE IF NOT EXISTS manual_invoices_vendor_id_backfill (
            invoice_id     BIGINT,
            original_value TEXT,
            recorded_at    TIMESTAMP DEFAULT NOW()
        );
        INSERT INTO manual_invoices_vendor_id_backfill (invoice_id, original_value)
        SELECT id, vendor_id
          FROM manual_invoices
         WHERE vendor_id IS NOT NULL
           AND vendor_id !~ '^[0-9]+$';

        UPDATE manual_invoices SET vendor_id = NULL
         WHERE vendor_id IS NOT NULL
           AND vendor_id !~ '^[0-9]+$';

        ALTER TABLE manual_invoices
            ALTER COLUMN vendor_id TYPE BIGINT USING NULLIF(vendor_id, '')::BIGINT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_manual_invoices_vendor_id           ON manual_invoices (vendor_id);

-- FK to vendor_info (deferred validation in case orphans remain)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_manual_invoices_vendor'
           AND conrelid = 'invoice.manual_invoices'::regclass
    ) THEN
        ALTER TABLE invoice.manual_invoices
            ADD CONSTRAINT fk_manual_invoices_vendor
            FOREIGN KEY (vendor_id) REFERENCES invoice.vendor_info(vendor_id)
            ON UPDATE NO ACTION ON DELETE SET NULL
            NOT VALID;
    END IF;
END $$;
