-- Add auth admin_id to updated_profile so the scheduler can join to manual_invoices.admin_id
-- Guarded: updated_profile and manage_users may be Hibernate-managed.

DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'invoice' AND table_name = 'updated_profile'
  ) THEN
    ALTER TABLE invoice.updated_profile ADD COLUMN IF NOT EXISTS admin_id BIGINT;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'invoice' AND table_name = 'manage_users'
    ) THEN
      UPDATE invoice.updated_profile up
      SET    admin_id = mu.admin_id
      FROM   invoice.manage_users mu
      WHERE  LOWER(mu.primary_email) = LOWER(up.primary_email)
        AND  mu.admin_id IS NOT NULL;
    END IF;
  END IF;
END $$;
