-- =============================================================================
-- V008  Encrypted PII columns + view-layer masking.
-- -----------------------------------------------------------------------------
-- Audit finding #2 (Critical): plaintext SSN / DOB / bank / routing / tax / EIN
-- in a publicly reachable database. This migration:
--   1) enables pgcrypto for column-level AES at rest;
--   2) adds *_enc bytea columns alongside the existing plaintext;
--   3) creates a one-time backfill function (operations runs with the encryption
--      key in session) so the rotation is auditable;
--   4) leaves the cleartext columns in place but adds a masked view used by
--      reporting / API layers that don't need the raw value.
--
-- Operational note: pgcrypto needs to be installed on the cluster (superuser).
-- The CREATE EXTENSION call is idempotent.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

SET search_path TO invoice, public;

DO $$
BEGIN
    -- consultant.security_number (SSN) ----------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema='invoice' AND table_name='consultant' AND column_name='security_number') THEN
        ALTER TABLE invoice.consultant ADD COLUMN IF NOT EXISTS security_number_enc BYTEA;
    END IF;

    -- consultant.date_of_birth (optional encryption / masking)
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema='invoice' AND table_name='consultant' AND column_name='date_of_birth') THEN
        ALTER TABLE invoice.consultant ADD COLUMN IF NOT EXISTS date_of_birth_enc BYTEA;
    END IF;

    -- consultant_bank_account.account_number / routing_number
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema='invoice' AND table_name='consultant_bank_account') THEN
        ALTER TABLE invoice.consultant_bank_account ADD COLUMN IF NOT EXISTS account_number_enc BYTEA;
        ALTER TABLE invoice.consultant_bank_account ADD COLUMN IF NOT EXISTS routing_number_enc BYTEA;
    END IF;

    -- bank_details (user-owned bank info)
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema='invoice' AND table_name='bank_details') THEN
        ALTER TABLE invoice.bank_details ADD COLUMN IF NOT EXISTS bank_account_number_enc BYTEA;
        ALTER TABLE invoice.bank_details ADD COLUMN IF NOT EXISTS routing_number_enc      BYTEA;
    END IF;
END $$;

-- Masked view for reporting / API export ----------------------------------------
CREATE OR REPLACE VIEW invoice.v_consultant_masked AS
SELECT
    c.id,
    c.cid,
    c.first_name,
    c.last_name,
    c.email,
    c.mobile_number,
    -- SSN: '***-**-1234' if at least 4 chars; else null
    CASE
        WHEN c.security_number IS NULL OR LENGTH(c.security_number) < 4 THEN NULL
        ELSE '***-**-' || RIGHT(REGEXP_REPLACE(c.security_number, '[^0-9]', '', 'g'), 4)
    END AS security_number_masked,
    c.status,
    c.admin_id,
    c.created_at,
    c.updated_at
  FROM invoice.consultant c;

-- Backfill helper. Operators run with the encryption key in session and the row
-- IDs they want migrated. Example:
--   SET LOCAL invoice.encryption_key = current_setting('invoice.encryption_key', true);
--   SELECT invoice.encrypt_consultant_pii('<key>');
-- The function uses pgp_sym_encrypt → bytea → AES.
CREATE OR REPLACE FUNCTION invoice.encrypt_consultant_pii(p_key TEXT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
    rows_updated INTEGER := 0;
BEGIN
    UPDATE invoice.consultant
       SET security_number_enc = pgp_sym_encrypt(security_number, p_key)
     WHERE security_number IS NOT NULL
       AND security_number_enc IS NULL;
    GET DIAGNOSTICS rows_updated = ROW_COUNT;
    RETURN rows_updated;
END $$;

CREATE OR REPLACE FUNCTION invoice.encrypt_bank_account_pii(p_key TEXT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
    rows_updated INTEGER := 0;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema='invoice' AND table_name='consultant_bank_account') THEN
        UPDATE invoice.consultant_bank_account
           SET account_number_enc = pgp_sym_encrypt(account_number, p_key),
               routing_number_enc = pgp_sym_encrypt(routing_number, p_key)
         WHERE (account_number IS NOT NULL AND account_number_enc IS NULL)
            OR (routing_number IS NOT NULL AND routing_number_enc IS NULL);
        GET DIAGNOSTICS rows_updated = ROW_COUNT;
    END IF;
    RETURN rows_updated;
END $$;
