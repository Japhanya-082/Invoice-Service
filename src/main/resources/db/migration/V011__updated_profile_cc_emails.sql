-- CC email addresses stored per admin for outgoing invoice emails
ALTER TABLE invoice.updated_profile
    ADD COLUMN IF NOT EXISTS cc_admin_email    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cc_hr_email       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cc_accounts_email VARCHAR(255);
