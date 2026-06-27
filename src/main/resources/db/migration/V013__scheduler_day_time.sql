DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'invoice' AND table_name = 'updated_profile') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'invoice' AND table_name = 'updated_profile' AND column_name = 'scheduler_day') THEN
            ALTER TABLE invoice.updated_profile ADD COLUMN scheduler_day VARCHAR(10);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'invoice' AND table_name = 'updated_profile' AND column_name = 'scheduler_time') THEN
            ALTER TABLE invoice.updated_profile ADD COLUMN scheduler_time VARCHAR(5);
        END IF;
    END IF;
END $$;
