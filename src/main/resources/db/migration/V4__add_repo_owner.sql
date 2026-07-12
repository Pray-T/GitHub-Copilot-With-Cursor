SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'repo_owner'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD COLUMN repo_owner VARCHAR(255) NULL COMMENT ''GitHub owner (user or org)'' AFTER id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE repository_workspace
SET repo_owner = LOWER(
    SUBSTRING_INDEX(
        SUBSTRING_INDEX(
            REPLACE(REPLACE(TRIM(TRAILING '/' FROM repo_url), '.git', ''), 'https://github.com/', ''),
            '/',
            1
        ),
        '?',
        1
    )
)
WHERE repo_owner IS NULL OR repo_owner = '';

UPDATE repository_workspace
SET repo_owner = 'unknown'
WHERE repo_owner IS NULL OR repo_owner = '';

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'repository_workspace'
          AND column_name = 'repo_owner'
          AND is_nullable = 'YES'
    ),
    'ALTER TABLE repository_workspace MODIFY repo_owner VARCHAR(255) NOT NULL',
    'SELECT 1'
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
          AND index_name = 'uk_repository_workspace_repo_name'
    ),
    'ALTER TABLE repository_workspace DROP INDEX uk_repository_workspace_repo_name',
    'SELECT 1'
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
          AND index_name = 'uk_repository_workspace_owner_name'
    ),
    'SELECT 1',
    'ALTER TABLE repository_workspace ADD CONSTRAINT uk_repository_workspace_owner_name UNIQUE (repo_owner, repo_name)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
