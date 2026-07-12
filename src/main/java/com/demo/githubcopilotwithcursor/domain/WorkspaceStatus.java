package com.demo.githubcopilotwithcursor.domain;

public enum WorkspaceStatus {
    CLONED,
    IDE_LAUNCHED,
    IDE_LAUNCH_FAILED,
    CREATED,
    AGENT_PENDING,
    AGENT_RUNNING,
    AGENT_SYNCING,
    READY_FOR_REVIEW,
    AGENT_FAILED,
    PR_OPENED
}
