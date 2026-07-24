package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Contribute 진행 상태 응답 (v3 Agent·LLM 캐시 메타 포함)")
public record ContributeStatusResponse(
    @Schema(description = "GitHub owner (user or org)", example = "spring-projects")
    String repoOwner,
    @Schema(description = "워크스페이스 식별용 저장소 이름", example = "spring-petclinic")
    String repoName,
    @Schema(description = "Review / Contribute 모드", example = "CONTRIBUTE")
    String mode,
    @Schema(description = "fork 정보")
    ForkInfo fork,
    @Schema(description = "현재 작업 브랜치", example = "refactor/spring-petclinic-202605041930")
    String branch,
    @Schema(description = "Cursor Cloud Agent 요약")
    AgentSummary agent,
    @Schema(description = "마지막 로컬 커밋 정보")
    LastCommit lastCommit,
    @Schema(description = "생성된 PR URL", example = "https://github.com/spring-projects/spring-petclinic/pull/12345", nullable = true)
    String prUrl,
    @Schema(description = "PR 상태(open/closed/merged/unknown)", example = "open", nullable = true)
    String prState,
    @Schema(description = "Composer LLM 캐시 메타 (본문 미포함)")
    LlmCacheSummary llmCache
) {

    @Schema(description = "fork 요약 정보")
    public record ForkInfo(String url, boolean reused) {
    }

    @Schema(description = "Cursor Agent 요약")
    public record AgentSummary(
        @Schema(description = "Cursor agent ID", example = "bc-abc123")
        String agentId,
        @Schema(description = "Agent 상태", example = "COMPLETED")
        String status,
        @Schema(description = "Agent 시작 시각", nullable = true)
        OffsetDateTime startedAt,
        @Schema(description = "Agent 완료 시각", nullable = true)
        OffsetDateTime completedAt
    ) {
    }

    @Schema(description = "마지막 커밋 정보")
    public record LastCommit(String sha, String message, OffsetDateTime pushedAt) {
    }

    @Schema(description = "LLM 캐시 존재 여부 (본문은 /pr/prepare 응답에서만 제공)")
    public record LlmCacheSummary(
        @Schema(description = "캐시 시각", nullable = true)
        OffsetDateTime cachedAt,
        @Schema(description = "commitMessage 캐시 존재")
        boolean hasCommitMessage,
        @Schema(description = "prTitle 캐시 존재")
        boolean hasPrTitle,
        @Schema(description = "prBody 캐시 존재")
        boolean hasPrBody,
        @Schema(description = "diff fingerprint 캐시 존재")
        boolean hasFingerprint
    ) {
    }
}
