-- =============================================================================
-- V009  Correct AR/AP status separation in the base invoice schema.
-- Guarded: manual_invoices may be Hibernate-managed and absent at migration time.
-- =============================================================================

SET search_path TO invoice, public;

DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'invoice' AND table_name = 'manual_invoices'
  ) THEN
    -- AR (receivable): PAID-family → RECEIVED-family
    UPDATE manual_invoices
    SET status = CASE UPPER(status)
        WHEN 'PAID'           THEN 'RECEIVED'
        WHEN 'PARTIALLY_PAID' THEN 'PARTIALLY_RECEIVED'
        WHEN 'EXCESS_PAID'    THEN 'EXCESS_RECEIVED'
        ELSE status
    END
    WHERE LOWER(vendor_type) = 'receivable'
      AND UPPER(status) IN ('PAID', 'PARTIALLY_PAID', 'EXCESS_PAID');

    -- AP (payable): RECEIVED-family → PAID-family
    UPDATE manual_invoices
    SET status = CASE UPPER(status)
        WHEN 'RECEIVED'           THEN 'PAID'
        WHEN 'PARTIALLY_RECEIVED' THEN 'PARTIALLY_PAID'
        WHEN 'EXCESS_RECEIVED'    THEN 'EXCESS_PAID'
        ELSE status
    END
    WHERE LOWER(vendor_type) = 'payable'
      AND UPPER(status) IN ('RECEIVED', 'PARTIALLY_RECEIVED', 'EXCESS_RECEIVED');
  END IF;
END $$;
