package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Contribute 모드 시작 결과")
public record ContributeStartResponse(
    @Schema(description = "GitHub owner (user or org)", example = "spring-projects")
    String repoOwner,
    @Schema(description = "워크스페이스 식별용 저장소 이름", example = "spring-petclinic")
    String repoName,
    @Schema(description = "원본 upstream 저장소 URL", example = "https://github.com/spring-projects/spring-petclinic")
    String upstreamUrl,
    @Schema(description = "사용자 fork 저장소 URL", example = "https://github.com/octocat/spring-petclinic")
    String forkUrl,
    @Schema(description = "기존 fork를 재사용했는지 여부", example = "true")
    boolean forkReused,
    @Schema(description = "생성된 feature branch 이름", example = "refactor/spring-petclinic-202605041930")
    String branchName,
    @Schema(description = "로컬 워크스페이스 절대경로")
    String workspacePath,
    @Schema(description = "클론 직후 HEAD 커밋 SHA")
    String headCommitSha,
    @Schema(description = "워크스페이스 상태", example = "AGENT_RUNNING")
    String status,
    @Schema(description = "클론 완료 시각")
    OffsetDateTime clonedAt,
    @Schema(description = "Cursor Cloud Agent ID")
    String agentId,
    @Schema(description = "Cursor Run ID")
    String runId,
    @Schema(description = "Agent 상태", example = "RUNNING")
    String agentStatus,
    @Schema(description = "Agent 시작 시각")
    OffsetDateTime agentStartedAt
) {
}
