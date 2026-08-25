ALTER TABLE users RENAME COLUMN user_type TO role;
ALTER TABLE users RENAME CONSTRAINT ck_users_user_type TO ck_users_role;
COMMENT ON COLUMN users.role IS 'CUSTOMER or MERCHANT, as classified by Auth Service.';