SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'fork_reused'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN fork_reused TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''기존 fork 재사용 여부. Contribute 모드에서만 의미 있음'' AFTER fork_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
