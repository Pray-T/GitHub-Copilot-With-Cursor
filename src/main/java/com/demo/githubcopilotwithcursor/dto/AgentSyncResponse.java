package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record AgentSyncResponse(
    String repoOwner,
    String repoName,
    String syncedHeadSha,
    OffsetDateTime syncedAt,
    String agentStatus,
    String status
) {
}
