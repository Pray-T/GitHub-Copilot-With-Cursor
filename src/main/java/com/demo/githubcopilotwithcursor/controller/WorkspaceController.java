package com.demo.githubcopilotwithcursor.controller;

import com.demo.githubcopilotwithcursor.dto.LaunchIdeResponse;
import com.demo.githubcopilotwithcursor.dto.WorkspaceListResponse;
import com.demo.githubcopilotwithcursor.service.LaunchIdeService;
import com.demo.githubcopilotwithcursor.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Workspace", description = "워크스페이스 목록·삭제·M1 IDE 실행")
@Validated
@RestController
@RequestMapping("/api")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final LaunchIdeService launchIdeService;

    public WorkspaceController(WorkspaceService workspaceService, LaunchIdeService launchIdeService) {
        this.workspaceService = workspaceService;
        this.launchIdeService = launchIdeService;
    }

    @GetMapping("/workspaces")
    @Operation(summary = "워크스페이스 목록", description = "활성 워크스페이스 목록을 mode·agentStatus·PR URL 메타와 함께 반환합니다.")
    public ResponseEntity<WorkspaceListResponse> list() {
        return ResponseEntity.ok(workspaceService.listWorkspaces());
    }

    @PostMapping("/workspaces/{repoOwner}/{repoName}/launch-ide")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "로컬 IDE 실행 (M1)", description = "Diff 확인 후 추가 수정이 필요할 때 Cursor IDE를 비동기로 실행합니다.")
    @ApiResponse(responseCode = "202", description = "IDE 실행 요청 수락")
    @ApiResponse(responseCode = "502", description = "IDE 실행 실패", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    public LaunchIdeResponse launchIde(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName
    ) {
        return launchIdeService.launchIde(repoOwner, repoName);
    }

    @DeleteMapping({
        "/workspaces/{repoOwner}/{repoName}",
        "/workspace/{repoOwner}/{repoName}"
    })
    @Operation(summary = "워크스페이스 삭제", description = "DB 메타데이터를 먼저 삭제하고 로컬 디스크 정리는 백그라운드로 수행합니다.")
    public ResponseEntity<Void> delete(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName
    ) {
        workspaceService.deleteWorkspace(repoOwner, repoName);
        return ResponseEntity.noContent().build();
    }
}
