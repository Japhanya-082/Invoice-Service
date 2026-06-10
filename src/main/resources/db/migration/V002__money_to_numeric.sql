-- =============================================================================
-- V002  Money to NUMERIC(19,4).
-- -----------------------------------------------------------------------------
-- Removes the audit's most damaging finding: float-precision artifacts in
-- aggregated totals (998463.1899999998, …). Every monetary column on
-- manual_invoices, invoice_items, contributions, vendor_info.discount is
-- converted to NUMERIC(19,4). Existing varchar money columns are reparsed.
-- =============================================================================

SET search_path TO invoice, public;

-- ---------------------------------------------------------------------------------
-- manual_invoices: Double → NUMERIC(19,4)
-- ---------------------------------------------------------------------------------
DO $$
DECLARE
    col RECORD;
    cols TEXT[] := ARRAY['total_hours','subtotal','tax','total','amount_due','credit','discount'];
    c TEXT;
BEGIN
    FOREACH c IN ARRAY cols LOOP
        SELECT data_type INTO col
          FROM information_schema.columns
         WHERE table_schema = 'invoice'
           AND table_name   = 'manual_invoices'
           AND column_name  = c;
        IF FOUND AND col.data_type IN ('double precision','real','numeric') THEN
            EXECUTE format(
                'ALTER TABLE invoice.manual_invoices ALTER COLUMN %I TYPE NUMERIC(19,4) USING ROUND(COALESCE(%I,0)::NUMERIC,4)', c, c);
            EXECUTE format(
                'ALTER TABLE invoice.manual_invoices ALTER COLUMN %I SET DEFAULT 0', c);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------
-- manual_invoices: String money fields → NUMERIC(19,4)
-- ---------------------------------------------------------------------------------
DO $$
DECLARE
    cols TEXT[] := ARRAY['payment_amount','due_amount','paid_amount'];
    c TEXT;
    col RECORD;
BEGIN
    FOREACH c IN ARRAY cols LOOP
        SELECT data_type INTO col
          FROM information_schema.columns
         WHERE table_schema = 'invoice'
           AND table_name   = 'manual_invoices'
           AND column_name  = c;
        IF FOUND AND col.data_type IN ('character varying','text') THEN
            EXECUTE format(
                'ALTER TABLE invoice.manual_invoices ALTER COLUMN %I TYPE NUMERIC(19,4) USING CASE WHEN %I IS NULL OR btrim(%I) = '''' THEN NULL ELSE ROUND(NULLIF(regexp_replace(%I, ''[^0-9.\-]'','''',''g''),'''')::NUMERIC,4) END', c, c, c, c);
            EXECUTE format(
                'ALTER TABLE invoice.manual_invoices ALTER COLUMN %I SET DEFAULT 0', c);
        ELSIF FOUND AND col.data_type IN ('double precision','real','numeric') THEN
            EXECUTE format(
                'ALTER TABLE invoice.manual_invoices ALTER COLUMN %I TYPE NUMERIC(19,4) USING ROUND(COALESCE(%I,0)::NUMERIC,4)', c, c);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------
-- invoice_items: hours, rate, amount → NUMERIC(19,4)
-- ---------------------------------------------------------------------------------
DO $$
DECLARE
    cols TEXT[] := ARRAY['hours','rate','amount'];
    c TEXT;
    col RECORD;
BEGIN
    FOREACH c IN ARRAY cols LOOP
        SELECT data_type INTO col
          FROM information_schema.columns
         WHERE table_schema = 'invoice'
           AND table_name   = 'invoice_items'
           AND column_name  = c;
        IF FOUND AND col.data_type IN ('double precision','real','numeric') THEN
            EXECUTE format(
                'ALTER TABLE invoice.invoice_items ALTER COLUMN %I TYPE NUMERIC(19,4) USING ROUND(COALESCE(%I,0)::NUMERIC,4)', c, c);
            EXECUTE format(
                'ALTER TABLE invoice.invoice_items ALTER COLUMN %I SET DEFAULT 0', c);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------
-- contributions.amount and vendor_info.discount → NUMERIC(19,4)
-- ---------------------------------------------------------------------------------
DO $$
DECLARE
    col RECORD;
BEGIN
    SELECT data_type INTO col FROM information_schema.columns
     WHERE table_schema = 'invoice' AND table_name='contributions' AND column_name='amount';
    IF FOUND AND col.data_type IN ('double precision','real','numeric') THEN
        ALTER TABLE invoice.contributions
            ALTER COLUMN amount TYPE NUMERIC(19,4) USING ROUND(COALESCE(amount,0)::NUMERIC,4);
    END IF;

    SELECT data_type INTO col FROM information_schema.columns
     WHERE table_schema = 'invoice' AND table_name='vendor_info' AND column_name='discount';
    IF FOUND AND col.data_type IN ('double precision','real','numeric') THEN
        ALTER TABLE invoice.vendor_info
            ALTER COLUMN discount TYPE NUMERIC(19,4) USING ROUND(COALESCE(discount,0)::NUMERIC,4);
        ALTER TABLE invoice.vendor_info ALTER COLUMN discount SET DEFAULT 0;
    END IF;
END $$;
