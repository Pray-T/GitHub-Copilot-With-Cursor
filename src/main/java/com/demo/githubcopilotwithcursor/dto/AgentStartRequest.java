package com.demo.githubcopilotwithcursor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentStartRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName,
    @Size(max = 8192) String agentPrompt
) {
}
