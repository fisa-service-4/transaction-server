CREATE TABLE user_master (
    user_id      NUMBER(19)    PRIMARY KEY,
    x_user_id    NUMBER(19)    NOT NULL,
    firebase_uid VARCHAR2(128) UNIQUE,
    user_name    VARCHAR2(100) NOT NULL,
    phone_number VARCHAR2(20)  NOT NULL,
    linked_at    TIMESTAMP,
    created_at   TIMESTAMP DEFAULT SYSTIMESTAMP
);

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
