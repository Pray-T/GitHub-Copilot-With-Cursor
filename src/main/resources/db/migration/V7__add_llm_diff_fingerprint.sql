SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'llm_diff_fingerprint'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN llm_diff_fingerprint VARCHAR(64) NULL COMMENT ''PR 메타 생성 시점 diff 스냅샷 SHA-256 hex'' AFTER llm_cached_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
