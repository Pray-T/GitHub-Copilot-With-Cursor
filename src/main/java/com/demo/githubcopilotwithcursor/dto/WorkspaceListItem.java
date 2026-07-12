package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "워크스페이스 목록 항목 (LLM 캐시 본문 미포함)")
public record WorkspaceListItem(
    @Schema(description = "GitHub owner", example = "spring-projects")
    String repoOwner,
    @Schema(description = "저장소 이름", example = "spring-petclinic")
    String repoName,
    @Schema(description = "원본 GitHub URL")
    String repoUrl,
    @Schema(description = "로컬 워크스페이스 경로")
    String workspacePath,
    @Schema(description = "워크스페이스 lifecycle 상태", example = "READY_FOR_REVIEW")
    String status,
    @Schema(description = "Review / Contribute 모드", example = "REVIEW")
    String mode,
    @Schema(description = "클론 시각")
    OffsetDateTime clonedAt,
    @Schema(description = "마지막 Diff 조회 시각", nullable = true)
    OffsetDateTime lastDiffAt,
    @Schema(description = "upstream URL (Contribute)", nullable = true)
    String upstreamUrl,
    @Schema(description = "fork URL", nullable = true)
    String forkUrl,
    @Schema(description = "fork 재사용 여부")
    boolean forkReused,
    @Schema(description = "feature branch 이름", nullable = true)
    String branchName,
    @Schema(description = "생성된 PR URL", nullable = true)
    String prUrl,
    @Schema(description = "Cursor Agent 상태", example = "COMPLETED", nullable = true)
    String agentStatus,
    @Schema(description = "Agent 시작 시각", nullable = true)
    OffsetDateTime agentStartedAt,
    @Schema(description = "Agent 완료 시각", nullable = true)
    OffsetDateTime agentCompletedAt,
    @Schema(description = "M1 IDE 실행 여부")
    boolean ideLaunched
) {
}
