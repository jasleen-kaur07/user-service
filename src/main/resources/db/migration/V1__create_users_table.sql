CREATE TABLE users (
    id          UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    phone       VARCHAR(20),
    user_type   VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),

    CONSTRAINT uq_users_email UNIQUE (email),

    CONSTRAINT ck_users_user_type CHECK (user_type IN ('CUSTOMER', 'MERCHANT'))
);

COMMENT ON TABLE  users            IS 'User profile. Identity (id) originates in Auth Service. No credentials are stored here.';
COMMENT ON COLUMN users.id         IS 'UUID assigned by Auth Service; shared identifier across all microservices.';
COMMENT ON COLUMN users.user_type  IS 'CUSTOMER or MERCHANT, as classified by Auth Service.';
