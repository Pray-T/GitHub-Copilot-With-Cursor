package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Pull Request 생성 요청")
public record PullRequestRequest(
    @Schema(description = "PR 제목", example = "Refactor Owner.java to use record")
    @NotBlank(message = "PR 제목을 입력하세요.")
    @Size(min = 1, max = 256)
    String title,

    @Schema(description = "PR 본문", example = "## 변경 파일\n\n- M src/main/java/.../Owner.java")
    @NotBlank(message = "PR 본문을 입력하세요.")
    @Size(max = 65535)
    String body,

    @Schema(description = "base 브랜치. 비우면 upstream default branch를 사용", example = "main", nullable = true)
    String base,

    @Schema(description = "draft PR 여부. null이면 true로 처리", example = "true", nullable = true)
    Boolean draft
) {
}
