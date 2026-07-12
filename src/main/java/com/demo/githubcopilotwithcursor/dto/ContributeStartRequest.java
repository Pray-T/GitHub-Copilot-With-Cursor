package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Contribute 모드 시작 요청")
public record ContributeStartRequest(
    @Schema(description = "upstream GitHub 저장소 URL", example = "https://github.com/spring-projects/spring-petclinic")
    @NotBlank(message = "GitHub 저장소 URL을 입력하세요.")
    @Size(max = 2048)
    @Pattern(
        regexp = "^https://github\\.com/[^/]+/[^/]+(?:\\.git)?/?$",
        message = "GitHub 저장소 URL 형식이 올바르지 않습니다."
    )
    String repoUrl
) {
}
