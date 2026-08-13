SET @registration_constraint_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'activity_registrations'
              AND index_name = 'uk_activity_regs_student_activity'
        ),
        'SELECT 1',
        'ALTER TABLE activity_registrations ADD CONSTRAINT uk_activity_regs_student_activity UNIQUE (student_id, activity_id)'
    )
);
PREPARE registration_constraint_stmt FROM @registration_constraint_sql;
EXECUTE registration_constraint_stmt;
DEALLOCATE PREPARE registration_constraint_stmt;

SET @source_key_column_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'training_point_details'
              AND column_name = 'source_key'
        ),
        'SELECT 1',
        'ALTER TABLE training_point_details ADD COLUMN source_key VARCHAR(160) NULL'
    )
);
PREPARE source_key_column_stmt FROM @source_key_column_sql;
EXECUTE source_key_column_stmt;
DEALLOCATE PREPARE source_key_column_stmt;

UPDATE training_point_details
SET source_key = CASE
    WHEN source_type IN ('POINT_REQUEST', 'MANUAL')
        THEN CONCAT(source_type, ':', criteria_code)
    WHEN source_type IS NOT NULL AND reference_id IS NOT NULL
        THEN CONCAT(source_type, ':', reference_id)
    ELSE CONCAT(COALESCE(source_type, 'LEGACY'), ':', criteria_code, ':', id)
END
WHERE source_key IS NULL OR source_key = '';

SET @score_constraint_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'training_point_details'
              AND index_name = 'uk_tp_detail_source_key'
        ),
        'SELECT 1',
        'ALTER TABLE training_point_details ADD CONSTRAINT uk_tp_detail_source_key UNIQUE (student_training_point_id, source_key)'
    )
);
PREPARE score_constraint_stmt FROM @score_constraint_sql;
EXECUTE score_constraint_stmt;
DEALLOCATE PREPARE score_constraint_stmt;
