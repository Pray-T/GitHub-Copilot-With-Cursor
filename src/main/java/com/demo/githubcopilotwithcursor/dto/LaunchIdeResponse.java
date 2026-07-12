package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record LaunchIdeResponse(
    String repoOwner,
    String repoName,
    OffsetDateTime requestedAt,
    String ideCommand,
    String status,
    boolean ideLaunchPending
) {
}
