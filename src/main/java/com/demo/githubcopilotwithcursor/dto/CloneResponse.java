package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record CloneResponse(
    String repoOwner,
    String repoName,
    String mode,
    String upstreamUrl,
    String forkUrl,
    boolean forkReused,
    String branchName,
    String workspacePath,
    String headCommitSha,
    String status,
    String agentId,
    String runId,
    String agentStatus,
    OffsetDateTime agentStartedAt,
    OffsetDateTime clonedAt
) {
}
