MERGE INTO user_account_mapping t
USING (SELECT 0 AS account_id, 'SYSTEM' AS account_type, 0 AS user_id, 0 AS x_user_id FROM DUAL) s
ON (t.account_id = s.account_id AND t.account_type = s.account_type)
WHEN NOT MATCHED THEN
  INSERT (account_id, account_type, user_id, x_user_id)
  VALUES (s.account_id, s.account_type, s.user_id, s.x_user_id);
