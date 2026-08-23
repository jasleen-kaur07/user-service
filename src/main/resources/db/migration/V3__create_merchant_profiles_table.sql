CREATE TABLE merchant_profiles (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    business_name   VARCHAR(255) NOT NULL,
    business_email  VARCHAR(255),
    business_phone  VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_merchant_profiles PRIMARY KEY (id),

    CONSTRAINT uq_merchant_profiles_user_id UNIQUE (user_id),

    CONSTRAINT fk_merchant_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE  merchant_profiles         IS 'Basic merchant identity only. Products, stock, pricing and ratings belong to Merchant Service.';
COMMENT ON COLUMN merchant_profiles.id      IS 'This IS the merchantId shared with Merchant Service and Order Service.';
COMMENT ON COLUMN merchant_profiles.user_id IS 'Owning user; that user must have user_type = MERCHANT.';
