INSERT INTO user_account_mapping (account_id, account_type, user_id, x_user_id)
VALUES (0, 'SYSTEM', 0, 0)
ON CONFLICT (account_id, account_type) DO NOTHING;
