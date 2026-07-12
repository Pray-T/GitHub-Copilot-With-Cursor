CREATE TABLE IF NOT EXISTS repository_workspace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repo_name VARCHAR(255) NOT NULL COMMENT '도출된 식별자, ^[A-Za-z0-9._-]+$',
    repo_url VARCHAR(2048) NOT NULL COMMENT '입력된 GitHub URL 원본',
    workspace_path VARCHAR(1024) NOT NULL COMMENT '클론된 절대경로',
    head_commit_sha CHAR(40) NOT NULL COMMENT '클론 직후 HEAD SHA-1, Diff 기준',
    status VARCHAR(32) NOT NULL COMMENT 'CLONED | IDE_LAUNCHED | IDE_LAUNCH_FAILED',
    ide_launched TINYINT(1) NOT NULL DEFAULT 0,
    cloned_at DATETIME(3) NOT NULL,
    last_diff_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_repository_workspace_repo_name (repo_name),
    KEY idx_repository_workspace_cloned_at (cloned_at DESC),
    KEY idx_repository_workspace_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '클론된 워크스페이스의 메타데이터 (v1 baseline)';
