package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;

public record PrPrepareResponse(
    String repoOwner,
    String repoName,
    String commitMessage,
    String prTitle,
    String prBody,
    OffsetDateTime cachedAt,
    boolean hasLocalUncommittedChanges,
    String nextStep,
    boolean fallbackUsed,
    boolean metadataRegeneratedDueToDiffChange
) {
    public static final String NEXT_COMMIT_FORM = "COMMIT_FORM";
    public static final String NEXT_PR_FORM = "PR_FORM";
}
