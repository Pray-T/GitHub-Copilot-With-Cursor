package com.demo.githubcopilotwithcursor.cursor.dto;

import java.time.OffsetDateTime;

public record AgentStatusResponse(
    String agentId,
    String status,
    String reason,
    String latestCommitSha,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt
) {
    public boolean isTerminal() {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "FINISHED", "ERROR", "CANCELLED", "EXPIRED", "FAILED" -> true;
            default -> false;
        };
    }

    public boolean isSuccessful() {
        return status != null && "FINISHED".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "ERROR", "FAILED", "EXPIRED" -> true;
            default -> false;
        };
    }

    public boolean isCancelled() {
        return status != null && "CANCELLED".equalsIgnoreCase(status);
    }
}
