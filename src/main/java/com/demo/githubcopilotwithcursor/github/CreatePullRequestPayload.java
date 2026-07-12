package com.demo.githubcopilotwithcursor.github;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePullRequestPayload(
    @NotBlank String title,
    @NotBlank String body,
    @NotBlank String base,
    @NotBlank String head,
    @NotNull Boolean draft
) {
}
