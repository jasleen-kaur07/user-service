CREATE TABLE addresses (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    address_line1  VARCHAR(255) NOT NULL,
    address_line2  VARCHAR(255),
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(100) NOT NULL,
    country        VARCHAR(100) NOT NULL,
    pincode        VARCHAR(20)  NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_addresses PRIMARY KEY (id),

    CONSTRAINT fk_addresses_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_addresses_user_id ON addresses (user_id);

CREATE UNIQUE INDEX uq_addresses_one_default_per_user
    ON addresses (user_id)
    WHERE is_default;

COMMENT ON TABLE  addresses            IS 'Delivery addresses owned by a user. At most one default per user, enforced by uq_addresses_one_default_per_user.';
COMMENT ON COLUMN addresses.is_default IS 'Exactly one row per user may be TRUE (partial unique index).';
