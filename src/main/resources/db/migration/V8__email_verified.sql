-- NULL until the user opens the link from the verification email
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
