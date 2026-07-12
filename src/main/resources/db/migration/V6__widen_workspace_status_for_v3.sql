ALTER TABLE repository_workspace
    MODIFY COLUMN status VARCHAR(32) NOT NULL
        COMMENT 'WorkspaceStatus enum: v1/v2 values plus v3 agent lifecycle states';
