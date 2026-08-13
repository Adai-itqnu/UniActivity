ALTER TABLE password_reset_tokens
    MODIFY COLUMN otp_code VARCHAR(255) NOT NULL,
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;

CREATE INDEX idx_password_reset_lookup
    ON password_reset_tokens (email, type, used, created_at);
