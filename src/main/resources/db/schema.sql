CREATE TABLE user_account_mapping (
    account_id   NUMBER(19)   NOT NULL,
    account_type VARCHAR2(10) NOT NULL,
    user_id      NUMBER(19)   NOT NULL,
    x_user_id    NUMBER(19)   NOT NULL,
    created_at   TIMESTAMP    DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_user_account_mapping PRIMARY KEY (account_id, account_type)
);

CREATE TABLE transfer_user_mapping (
    transfer_id  NUMBER(19)   NOT NULL,
    x_user_id    NUMBER(19)   NOT NULL,
    created_at   TIMESTAMP    DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_transfer_user_mapping PRIMARY KEY (transfer_id)
);

CREATE TABLE order_user_mapping (
    order_id     NUMBER(19)   NOT NULL,
    x_user_id    NUMBER(19)   NOT NULL,
    created_at   TIMESTAMP    DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_order_user_mapping PRIMARY KEY (order_id)
);
