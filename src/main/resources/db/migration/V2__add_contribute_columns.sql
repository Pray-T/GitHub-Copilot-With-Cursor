SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'upstream_url'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN upstream_url VARCHAR(2048) NULL COMMENT ''원본(타인) 저장소 URL — Contribute 모드'' AFTER updated_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'fork_url'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN fork_url VARCHAR(2048) NULL COMMENT ''사용자 fork URL — Contribute 모드, 기존 fork 재사용 시도 동일'' AFTER upstream_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'branch_name'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN branch_name VARCHAR(255) NULL COMMENT ''작업 중인 feature branch (refactor/{repoName}-{yyyyMMddHHmm})'' AFTER fork_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'pr_url'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN pr_url VARCHAR(512) NULL COMMENT ''GitHub PR URL — PR 생성 후에만 채워짐'' AFTER branch_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND index_name = 'idx_repository_workspace_pr_url'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD INDEX idx_repository_workspace_pr_url (pr_url)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
