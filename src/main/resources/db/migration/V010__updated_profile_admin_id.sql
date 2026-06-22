-- Add auth admin_id to updated_profile so the scheduler can join to manual_invoices.admin_id
ALTER TABLE invoice.updated_profile ADD COLUMN IF NOT EXISTS admin_id BIGINT;

-- Back-fill from manage_users (admin_id = the owning admin's auth ID)
UPDATE invoice.updated_profile up
SET    admin_id = mu.admin_id
FROM   invoice.manage_users mu
WHERE  LOWER(mu.primary_email) = LOWER(up.primary_email)
  AND  mu.admin_id IS NOT NULL;
