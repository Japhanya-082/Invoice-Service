-- =============================================================================
-- V003  payments table — proper payment tracking, partial payments, balance derivation.
-- -----------------------------------------------------------------------------
-- Resolves audit findings:
--   * paid_amount NULL for 100% of invoices (no payment application logic)
--   * amount_due == total for every invoice including 'Received' / 'Partially Received'
--   * no reconciliation source-of-truth
-- =============================================================================

SET search_path TO invoice, public;

CREATE TABLE IF NOT EXISTS payments (
    payment_id          BIGSERIAL PRIMARY KEY,
    invoice_id          BIGINT       NOT NULL,
    admin_id            BIGINT       NOT NULL,
    amount              NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    payment_date        DATE         NOT NULL,
    payment_reference   VARCHAR(120),
    payment_method      VARCHAR(40),
    remarks             VARCHAR(1000),
    status              VARCHAR(30)  NOT NULL DEFAULT 'POSTED'
                          CHECK (status IN ('POSTED','VOIDED','PENDING','RETURNED')),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    deleted_at          TIMESTAMP,
    CONSTRAINT payments_invoice_id_nn CHECK (invoice_id IS NOT NULL)
);

-- Add FK only if manual_invoices already exists (it may be Hibernate-managed)
DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'invoice' AND table_name = 'manual_invoices'
  ) THEN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'invoice' AND table_name = 'payments'
          AND constraint_name = 'fk_payments_invoice'
    ) THEN
      ALTER TABLE invoice.payments
        ADD CONSTRAINT fk_payments_invoice
          FOREIGN KEY (invoice_id)
          REFERENCES invoice.manual_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT;
    END IF;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payments_invoice          ON payments (invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_admin            ON payments (admin_id);
CREATE INDEX IF NOT EXISTS idx_payments_admin_date       ON payments (admin_id, payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_status           ON payments (status) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_admin_ref  ON payments (admin_id, payment_reference)
    WHERE payment_reference IS NOT NULL AND payment_reference <> '';

-- Trigger: keep manual_invoices.amount_due / paid_amount / status in sync ----------
-- The view of truth is: paid_amount = SUM(payments.amount filtered to POSTED, not deleted)
-- amount_due = total - paid_amount
-- status transitions when amount_due crosses thresholds.
CREATE OR REPLACE FUNCTION invoice.recompute_invoice_balance(p_invoice_id BIGINT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    total_due       NUMERIC(19,4);
    paid_sum        NUMERIC(19,4);
    invoice_total   NUMERIC(19,4);
    current_status  TEXT;
    new_status      TEXT;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO paid_sum
      FROM invoice.payments
     WHERE invoice_id = p_invoice_id
       AND status = 'POSTED'
       AND deleted_at IS NULL;

    SELECT total, status INTO invoice_total, current_status
      FROM invoice.manual_invoices
     WHERE id = p_invoice_id;

    IF invoice_total IS NULL THEN
        RETURN;
    END IF;

    total_due := invoice_total - paid_sum;
    IF total_due < 0 THEN total_due := 0; END IF;

    new_status := current_status;
    IF current_status IN ('DRAFT','CANCELLED') THEN
        new_status := current_status;            -- terminal/early states untouched
    ELSIF paid_sum = 0 THEN
        new_status := 'PENDING';
    ELSIF total_due > 0 THEN
        new_status := 'PARTIALLY_PAID';
    ELSE
        new_status := 'PAID';
    END IF;

    UPDATE invoice.manual_invoices
       SET paid_amount = paid_sum,
           amount_due  = total_due,
           status      = new_status,
           updated_at  = NOW()
     WHERE id = p_invoice_id;
END $$;

CREATE OR REPLACE FUNCTION invoice.payments_after_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM invoice.recompute_invoice_balance(OLD.invoice_id);
        RETURN OLD;
    ELSE
        PERFORM invoice.recompute_invoice_balance(NEW.invoice_id);
        RETURN NEW;
    END IF;
END $$;

DROP TRIGGER IF EXISTS trg_payments_recompute ON invoice.payments;
CREATE TRIGGER trg_payments_recompute
AFTER INSERT OR UPDATE OR DELETE ON invoice.payments
FOR EACH ROW EXECUTE FUNCTION invoice.payments_after_change();

-- Initial backfill: derive paid_amount / amount_due / status for existing rows.
-- Guarded: manual_invoices may not exist yet if it is Hibernate-managed.
DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'invoice' AND table_name = 'manual_invoices'
  ) THEN
    UPDATE invoice.manual_invoices
       SET paid_amount = 0,
           amount_due  = COALESCE(total, 0),
           status      = CASE
                           WHEN LOWER(COALESCE(status,'')) IN ('paid','received')                              THEN 'PAID'
                           WHEN LOWER(COALESCE(status,'')) IN ('partially_paid','partially received','partial') THEN 'PARTIALLY_PAID'
                           WHEN LOWER(COALESCE(status,'')) IN ('cancelled','canceled')                         THEN 'CANCELLED'
                           WHEN LOWER(COALESCE(status,'')) IN ('draft')                                        THEN 'DRAFT'
                           WHEN LOWER(COALESCE(status,'')) IN ('overdue')                                      THEN 'OVERDUE'
                           ELSE 'PENDING'
                         END;
  END IF;
END $$;
