package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record AgentStartResponse(
    String repoOwner,
    String repoName,
    String agentId,
    String runId,
    String agentStatus,
    OffsetDateTime agentStartedAt
) {
}
