package com.demo.githubcopilotwithcursor.controller;

import com.demo.githubcopilotwithcursor.dto.AgentCancelResponse;
import com.demo.githubcopilotwithcursor.dto.AgentStartRequest;
import com.demo.githubcopilotwithcursor.dto.AgentStartResponse;
import com.demo.githubcopilotwithcursor.dto.AgentStatusJsonResponse;
import com.demo.githubcopilotwithcursor.dto.AgentSyncResponse;
import com.demo.githubcopilotwithcursor.service.AgentOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cursor Agent", description = "Cursor Cloud Agent 시작·상태·취소·동기화 API")
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final String REPO_SEGMENT_PATTERN = "^[A-Za-z0-9._-]+$";

    private final AgentOrchestratorService agentOrchestratorService;

    public AgentController(AgentOrchestratorService agentOrchestratorService) {
        this.agentOrchestratorService = agentOrchestratorService;
    }

    @PostMapping("/start")
    @Operation(summary = "Agent 재시작", description = "기존 워크스페이스에 대해 Cursor Cloud Agent를 다시 시작합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Agent 시작 요청 수락"),
        @ApiResponse(responseCode = "409", description = "다른 Agent 실행 중 (AGENT_BUSY)", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    })
    public ResponseEntity<AgentStartResponse> start(@Valid @RequestBody AgentStartRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(agentOrchestratorService.restartAgent(request));
    }

    @GetMapping("/{repoOwner}/{repoName}/status")
    @Operation(summary = "Agent 상태 조회", description = "Cursor Cloud Agent 진행 상태를 조회하고, 완료 시 fork branch를 ff-only pull로 동기화합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상태 조회 성공"),
        @ApiResponse(responseCode = "404", description = "Agent 미시작 (AGENT_NOT_FOUND)", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Agent 실패 또는 sync 실패", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    })
    public ResponseEntity<AgentStatusJsonResponse> status(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName
    ) {
        return ResponseEntity.ok(agentOrchestratorService.getStatus(repoOwner, repoName));
    }

    @PostMapping("/{repoOwner}/{repoName}/cancel")
    @Operation(summary = "작동중인 Agent 취소", description = "실행 중인 Cursor Cloud Agent를 취소합니다.")
    public ResponseEntity<AgentCancelResponse> cancel(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName
    ) {
        return ResponseEntity.ok(agentOrchestratorService.cancel(repoOwner, repoName));
    }

    @PostMapping("/{repoOwner}/{repoName}/sync")
    @Operation(summary = "Agent branch 수동 동기화", description = "origin feature branch를 ff-only pull로 동기화합니다.")
    public ResponseEntity<AgentSyncResponse> sync(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName
    ) {
        return ResponseEntity.ok(agentOrchestratorService.sync(repoOwner, repoName));
    }
}
