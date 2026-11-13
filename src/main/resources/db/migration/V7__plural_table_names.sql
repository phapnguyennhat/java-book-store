-- Convention: table names are plural. Constraint, index and sequence names keep their old prefix
-- (e.g. shop_kyc_status_check, shop_id_seq); reference them by those names in later migrations.
ALTER TABLE book RENAME TO books;
ALTER TABLE shop RENAME TO shops;
ALTER TABLE shop_kyc RENAME TO shop_kycs;
