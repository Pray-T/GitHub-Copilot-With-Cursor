package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Pull Request 생성 결과")
public record PullRequestResponse(
    @Schema(description = "GitHub owner (user or org)", example = "spring-projects")
    String repoOwner,
    @Schema(description = "워크스페이스 식별용 저장소 이름", example = "spring-petclinic")
    String repoName,
    @Schema(description = "생성된 PR URL", example = "https://github.com/spring-projects/spring-petclinic/pull/12345")
    String prUrl,
    @Schema(description = "생성된 PR 번호", example = "12345")
    int prNumber,
    @Schema(description = "PR 상태", example = "open")
    String state,
    @Schema(description = "draft PR 여부", example = "true")
    boolean draft,
    @Schema(description = "base 브랜치", example = "main")
    String base,
    @Schema(description = "head 브랜치", example = "octocat:refactor/spring-petclinic-202605041930")
    String head,
    @Schema(description = "PR 생성 시각")
    OffsetDateTime createdAt
) {
}
