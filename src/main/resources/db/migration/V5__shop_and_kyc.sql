CREATE TABLE shop (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       BIGINT       NOT NULL UNIQUE REFERENCES users (id), -- one shop per user
    name           VARCHAR(100) NOT NULL,
    slug           VARCHAR(50)  NOT NULL UNIQUE,
    description    VARCHAR(2000),
    phone          VARCHAR(16)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    pickup_address VARCHAR(500) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seller identity for the shop (sensitive: only the owner and admins may read it).
-- Person fields are the seller for INDIVIDUAL, the legal representative otherwise.
-- *_key columns are object keys in the private S3 bucket.
CREATE TABLE shop_kyc (
    shop_id               BIGINT       PRIMARY KEY REFERENCES shop (id) ON DELETE CASCADE,
    seller_type           VARCHAR(20)  NOT NULL CHECK (seller_type IN ('INDIVIDUAL', 'HOUSEHOLD_BUSINESS', 'COMPANY')),
    status                VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

    legal_name            VARCHAR(255) NOT NULL,
    id_number             CHAR(12)     NOT NULL,
    id_issue_date         DATE         NOT NULL,
    id_issue_place        VARCHAR(255) NOT NULL,
    date_of_birth         DATE         NOT NULL,
    permanent_address     VARCHAR(500) NOT NULL,
    id_front_key          VARCHAR(255) NOT NULL,
    id_back_key           VARCHAR(255) NOT NULL,
    selfie_key            VARCHAR(255) NOT NULL,

    business_name         VARCHAR(255),
    registration_number   VARCHAR(50),
    registration_date     DATE,
    tax_code              VARCHAR(14),
    registered_address    VARCHAR(500),
    business_license_key  VARCHAR(255),

    bank_code             VARCHAR(20)  NOT NULL,
    account_number        VARCHAR(20)  NOT NULL,
    account_holder        VARCHAR(255) NOT NULL,

    submitted_at          TIMESTAMPTZ  NOT NULL,
    reviewed_by           BIGINT       REFERENCES users (id),
    reviewed_at           TIMESTAMPTZ,
    rejection_reason      VARCHAR(1000),
    version               BIGINT       NOT NULL DEFAULT 0,

    CHECK ((seller_type = 'INDIVIDUAL') = (business_name IS NULL))
);
CREATE INDEX idx_shop_kyc_status ON shop_kyc (status, submitted_at);
