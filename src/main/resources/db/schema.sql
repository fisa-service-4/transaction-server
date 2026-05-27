CREATE TABLE IF NOT EXISTS user_account_mapping (
    account_id   BIGINT      NOT NULL,
    account_type VARCHAR(10) NOT NULL,
    user_id      BIGINT      NOT NULL,
    x_user_id    BIGINT      NOT NULL,
    created_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, account_type)
);

CREATE TABLE IF NOT EXISTS transfer_user_mapping (
    transfer_id BIGINT    NOT NULL,
    x_user_id   BIGINT    NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transfer_id)
);

CREATE TABLE IF NOT EXISTS order_user_mapping (
    order_id   BIGINT    NOT NULL,
    x_user_id  BIGINT    NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id)
);
