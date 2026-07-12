package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentStatusJsonResponse(
    String repoOwner,
    String repoName,
    String agentId,
    String agentStatus,
    String cursorStatus,
    OffsetDateTime agentStartedAt,
    OffsetDateTime agentCompletedAt,
    String syncedHeadSha,
    Map<String, Object> details
) {
}
