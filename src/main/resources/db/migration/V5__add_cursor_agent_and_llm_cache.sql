SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'mode'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT ''REVIEW'' COMMENT ''REVIEW | CONTRIBUTE'' AFTER fork_reused'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'cursor_agent_id'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN cursor_agent_id VARCHAR(128) NULL COMMENT ''Cursor Cloud Agent ID'' AFTER mode'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'cursor_run_id'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN cursor_run_id VARCHAR(128) NULL COMMENT ''Cursor Run ID'' AFTER cursor_agent_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'agent_prompt'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN agent_prompt TEXT NULL COMMENT ''사용자 입력 자연어 프롬프트'' AFTER cursor_run_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'agent_status'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN agent_status VARCHAR(32) NULL COMMENT ''PENDING|RUNNING|SYNCING|COMPLETED|FAILED|CANCELLED'' AFTER agent_prompt'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'agent_started_at'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN agent_started_at DATETIME(3) NULL AFTER agent_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'agent_completed_at'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN agent_completed_at DATETIME(3) NULL AFTER agent_started_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'llm_commit_message'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN llm_commit_message VARCHAR(4096) NULL COMMENT ''Composer 캐시(commit)'' AFTER agent_completed_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'llm_pr_title'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN llm_pr_title VARCHAR(256) NULL COMMENT ''Composer 캐시(title)'' AFTER llm_commit_message'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'llm_pr_body'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN llm_pr_body TEXT NULL COMMENT ''Composer 캐시(body)'' AFTER llm_pr_title'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'repository_workspace' AND column_name = 'llm_cached_at'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN llm_cached_at DATETIME(3) NULL COMMENT ''캐시 생성 시각'' AFTER llm_pr_body'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'repository_workspace'
      AND index_name = 'idx_repository_workspace_mode'
);
SET @ddl = IF(
    @idx_exists > 0,
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD INDEX idx_repository_workspace_mode (mode)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'repository_workspace'
      AND index_name = 'idx_repository_workspace_agent_status'
);
SET @ddl = IF(
    @idx_exists > 0,
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD INDEX idx_repository_workspace_agent_status (agent_status)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
