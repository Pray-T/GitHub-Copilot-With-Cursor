package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record AgentCancelResponse(
    String repoOwner,
    String repoName,
    String agentId,
    String agentStatus,
    OffsetDateTime cancelledAt
) {
}
