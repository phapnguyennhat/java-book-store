-- KYC drafts: the seller saves the wizard step by step (any field may still be missing) and submits when done.
-- Completeness is enforced when leaving DRAFT; the app validates the full rules, this is the DB safety net.
ALTER TABLE shop_kyc
    DROP CONSTRAINT shop_kyc_check,
    DROP CONSTRAINT shop_kyc_status_check,
    ADD CONSTRAINT shop_kyc_status_check CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    ALTER COLUMN seller_type DROP NOT NULL,
    ALTER COLUMN legal_name DROP NOT NULL,
    ALTER COLUMN id_number DROP NOT NULL,
    ALTER COLUMN id_issue_date DROP NOT NULL,
    ALTER COLUMN id_issue_place DROP NOT NULL,
    ALTER COLUMN date_of_birth DROP NOT NULL,
    ALTER COLUMN permanent_address DROP NOT NULL,
    ALTER COLUMN id_front_key DROP NOT NULL,
    ALTER COLUMN id_back_key DROP NOT NULL,
    ALTER COLUMN selfie_key DROP NOT NULL,
    ALTER COLUMN bank_code DROP NOT NULL,
    ALTER COLUMN account_number DROP NOT NULL,
    ALTER COLUMN account_holder DROP NOT NULL,
    ALTER COLUMN submitted_at DROP NOT NULL,
    ADD CONSTRAINT shop_kyc_complete_check CHECK (status = 'DRAFT' OR (
        seller_type IS NOT NULL AND legal_name IS NOT NULL AND id_number IS NOT NULL
        AND id_front_key IS NOT NULL AND id_back_key IS NOT NULL AND selfie_key IS NOT NULL
        AND account_number IS NOT NULL AND account_holder IS NOT NULL AND submitted_at IS NOT NULL
        AND (seller_type = 'INDIVIDUAL') = (business_name IS NULL)));
