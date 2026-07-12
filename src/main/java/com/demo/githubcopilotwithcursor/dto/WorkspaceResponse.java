package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record WorkspaceResponse(
    String repoOwner,
    String repoName,
    String repoUrl,
    String workspacePath,
    String status,
    String mode,
    OffsetDateTime clonedAt,
    OffsetDateTime lastDiffAt,
    String upstreamUrl,
    String forkUrl,
    boolean forkReused,
    String branchName,
    String prUrl,
    String agentStatus,
    OffsetDateTime agentStartedAt,
    OffsetDateTime agentCompletedAt,
    String agentPrompt,
    boolean ideLaunched,
    String llmCommitMessage,
    String llmPrTitle,
    String llmPrBody,
    OffsetDateTime llmCachedAt
) {
}
