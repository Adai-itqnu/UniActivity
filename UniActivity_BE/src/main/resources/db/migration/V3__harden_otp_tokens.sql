ALTER TABLE password_reset_tokens
    MODIFY COLUMN otp_code VARCHAR(255) NOT NULL;

SET @failed_attempts_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'password_reset_tokens'
              AND column_name = 'failed_attempts'
        ),
        'SELECT 1',
        'ALTER TABLE password_reset_tokens ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0'
    )
);
PREPARE failed_attempts_stmt FROM @failed_attempts_sql;
EXECUTE failed_attempts_stmt;
DEALLOCATE PREPARE failed_attempts_stmt;

SET @otp_index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'password_reset_tokens'
              AND index_name = 'idx_password_reset_lookup'
        ),
        'SELECT 1',
        'CREATE INDEX idx_password_reset_lookup ON password_reset_tokens (email, type, used, created_at)'
    )
);
PREPARE otp_index_stmt FROM @otp_index_sql;
EXECUTE otp_index_stmt;
DEALLOCATE PREPARE otp_index_stmt;
