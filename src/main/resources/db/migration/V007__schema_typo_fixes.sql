-- =============================================================================
-- V007  Fix schema typos baked into existing columns.
-- -----------------------------------------------------------------------------
-- moblie_number → mobile_number, periodend → period_end, visaenddate → visa_end_date,
-- visastartdate → visa_start_date. Each guarded so re-runs are safe.
-- =============================================================================

-- NEUTRALIZED: these renames moved columns away from the names the JPA entities
-- still map (Consultant.visaType -> "visatype", visaStartDate -> "visastartdate",
-- visaEndDate -> "visaenddate"; etc.). With spring.jpa.hibernate.ddl-auto=update,
-- Hibernate then re-created the old names as EMPTY duplicate columns, so the app
-- read NULL while the data sat in the renamed (visa_type/...) columns.
-- The entities keep the original column names, so we must NOT rename them.
-- Left as a no-op (the file is retained so Flyway history stays consistent).
SET search_path TO invoice, public;

DO $$
BEGIN
    -- intentionally empty: column-rename typo-fixes disabled (see note above)
    NULL;
END $$;



--
--SET search_path TO invoice, public;
--
--DO $$
--BEGIN
--    -- consultant
--    IF EXISTS (SELECT 1 FROM information_schema.columns
--                WHERE table_schema='invoice' AND table_name='consultant' AND column_name='visaenddate') THEN
--        ALTER TABLE invoice.consultant RENAME COLUMN visaenddate TO visa_end_date;
--    END IF;
--    IF EXISTS (SELECT 1 FROM information_schema.columns
--                WHERE table_schema='invoice' AND table_name='consultant' AND column_name='visastartdate') THEN
--        ALTER TABLE invoice.consultant RENAME COLUMN visastartdate TO visa_start_date;
--    END IF;
--
--    -- customer_info (if it still exists post-V006)
--    IF EXISTS (SELECT 1 FROM information_schema.columns
--                WHERE table_schema='invoice' AND table_name='customer_info' AND column_name='moblie_number') THEN
--        ALTER TABLE invoice.customer_info RENAME COLUMN moblie_number TO mobile_number;
--    END IF;
--
--    -- manual_invoices
--    IF EXISTS (SELECT 1 FROM information_schema.columns
--                WHERE table_schema='invoice' AND table_name='manual_invoices' AND column_name='periodend') THEN
--        ALTER TABLE invoice.manual_invoices RENAME COLUMN periodend TO period_end;
--    END IF;
--END $$;
