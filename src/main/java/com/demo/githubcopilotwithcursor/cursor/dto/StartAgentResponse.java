package com.demo.githubcopilotwithcursor.cursor.dto;

import java.time.OffsetDateTime;

public record StartAgentResponse(
    String agentId,
    String runId,
    String status,
    OffsetDateTime startedAt
) {
}
