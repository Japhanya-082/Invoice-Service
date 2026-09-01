-- CC email addresses stored per admin for outgoing invoice emails
-- Guarded: updated_profile may be Hibernate-managed.

DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'invoice' AND table_name = 'updated_profile'
  ) THEN
    ALTER TABLE invoice.updated_profile
        ADD COLUMN IF NOT EXISTS cc_admin_email    VARCHAR(255),
        ADD COLUMN IF NOT EXISTS cc_hr_email       VARCHAR(255),
        ADD COLUMN IF NOT EXISTS cc_accounts_email VARCHAR(255);
  END IF;
END $$;
