DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'invoice' AND table_name = 'manual_invoices') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'invoice' AND table_name = 'manual_invoices' AND column_name = 'reminder_snoozed_until') THEN
            ALTER TABLE invoice.manual_invoices ADD COLUMN reminder_snoozed_until DATE;
        END IF;
    END IF;
END $$;
