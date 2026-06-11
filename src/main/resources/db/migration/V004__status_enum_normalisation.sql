-- =============================================================================
-- V004  Normalise manual_invoices.status, enforce CHECK constraint.
-- -----------------------------------------------------------------------------
-- Audit found two competing vocabularies (CHECK enum on legacy `invoices`,
-- free-text Title-case on active manual_invoices) and missing OVERDUE / PAID.
-- =============================================================================

SET search_path TO invoice, public;

-- NEUTRALIZED: this migration originally uppercased statuses and FOLDED the
-- receivable "Partially Received"/"Received" into the payable PARTIALLY_PAID/PAID,
-- and added a CHECK constraint. That collapsed the AR vs AP distinction the app
-- relies on (reconciliation uses Received-side, payments uses Paid-side) and
-- forced UPPERCASE_UNDERSCORE that broke the title-case the UI sends/displays.
-- Statuses are now kept exactly as the frontend uses them (title case, distinct
-- per AR/AP), with no enforcing constraint. Left as a no-op so Flyway history
-- stays consistent.
DO $$
BEGIN
    NULL;
END $$;
