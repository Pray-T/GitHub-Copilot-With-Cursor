package com.demo.githubcopilotwithcursor.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DiffResponse(
    String repoOwner,
    String repoName,
    String headCommitSha,
    OffsetDateTime comparedAt,
    int totalChangedFiles,
    List<ChangedFileResponse> changedFiles
) {
}
