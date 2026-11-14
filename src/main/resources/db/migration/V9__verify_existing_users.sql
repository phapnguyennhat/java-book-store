-- login now requires a verified email; accounts made before verification existed keep working
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;
