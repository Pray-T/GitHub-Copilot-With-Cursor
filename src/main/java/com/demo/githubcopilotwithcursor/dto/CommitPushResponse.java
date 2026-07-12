package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "커밋 및 push 결과")
public record CommitPushResponse(
    @Schema(description = "GitHub owner (user or org)", example = "spring-projects")
    String repoOwner,
    @Schema(description = "워크스페이스 식별용 저장소 이름", example = "spring-petclinic")
    String repoName,
    @Schema(description = "push 대상 feature branch", example = "refactor/spring-petclinic-202605041930")
    String branchName,
    @Schema(description = "생성된 커밋 SHA", example = "f1e2d3c4b5a69788877665544332211009988aa")
    String commitSha,
    @Schema(description = "push 대상 fork URL", example = "https://github.com/octocat/spring-petclinic")
    String pushedTo,
    @Schema(description = "push 완료 시각")
    OffsetDateTime pushedAt
) {
}
