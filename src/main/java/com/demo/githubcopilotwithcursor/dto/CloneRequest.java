package com.demo.githubcopilotwithcursor.dto;

import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CloneRequest(
    @NotBlank(message = "GitHub 저장소 URL을 입력하세요.")
    @Size(max = 2048)
    @Pattern(regexp = "^https://github\\.com/[^/]+/[^/]+(?:\\.git)?$", message = "GitHub 저장소 URL 형식이 올바르지 않습니다.")
    String repoUrl,
    @NotBlank(message = "Agent 프롬프트는 비워둘 수 없습니다.")
    @Size(min = 1, max = 8192, message = "Agent 프롬프트는 1~8192자여야 합니다.")
    String agentPrompt,
    WorkspaceMode mode
) {
    public WorkspaceMode resolvedMode() {
        return mode == null ? WorkspaceMode.REVIEW : mode;
    }
}
