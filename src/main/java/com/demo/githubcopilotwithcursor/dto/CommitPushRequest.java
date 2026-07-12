package com.demo.githubcopilotwithcursor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "커밋 및 push 요청")
public record CommitPushRequest(
    @Schema(description = "커밋 메시지", example = "Refactor Owner.java to use record")
    @NotBlank(message = "커밋 메시지를 입력하세요.")
    @Size(min = 1, max = 4096)
    String message,

    @Schema(description = "커밋 작성자 이름", example = "The Octocat")
    @NotBlank(message = "작성자 이름을 입력하세요.")
    @Size(max = 255)
    String authorName,

    @Schema(description = "커밋 작성자 이메일", example = "octocat@example.com")
    @NotBlank
    @Email(message = "올바른 이메일 주소를 입력하세요.")
    @Size(max = 255)
    String authorEmail
) {
}
