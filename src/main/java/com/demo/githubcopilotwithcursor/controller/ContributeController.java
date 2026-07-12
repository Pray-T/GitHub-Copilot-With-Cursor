package com.demo.githubcopilotwithcursor.controller;

import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.dto.CommitPushResponse;
import com.demo.githubcopilotwithcursor.dto.ContributeStatusResponse;
import com.demo.githubcopilotwithcursor.dto.ContributeStartRequest;
import com.demo.githubcopilotwithcursor.dto.ContributeStartResponse;
import com.demo.githubcopilotwithcursor.dto.PrPrepareResponse;
import com.demo.githubcopilotwithcursor.dto.PullRequestRequest;
import com.demo.githubcopilotwithcursor.dto.PullRequestResponse;
import com.demo.githubcopilotwithcursor.service.CommitPushService;
import com.demo.githubcopilotwithcursor.service.ContributeService;
import com.demo.githubcopilotwithcursor.service.ContributeStatusService;
import com.demo.githubcopilotwithcursor.service.LlmMetadataService;
import com.demo.githubcopilotwithcursor.service.PullRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/contribute")
public class ContributeController {

    private final ContributeService contributeService;
    private final CommitPushService commitPushService;
    private final PullRequestService pullRequestService;
    private final ContributeStatusService contributeStatusService;
    private final LlmMetadataService llmMetadataService;

    public ContributeController(
        ContributeService contributeService,
        CommitPushService commitPushService,
        PullRequestService pullRequestService,
        ContributeStatusService contributeStatusService,
        LlmMetadataService llmMetadataService
    ) {
        this.contributeService = contributeService;
        this.commitPushService = commitPushService;
        this.pullRequestService = pullRequestService;
        this.contributeStatusService = contributeStatusService;
        this.llmMetadataService = llmMetadataService;
    }

    @PostMapping("/start")
    @Operation(summary = "Contribute 워크스페이스 시작", description = "upstream 저장소를 fork 재사용/생성하고 clone, feature branch 생성, Cursor IDE 실행까지 한 번에 수행합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contribute 시작 성공"),
        @ApiResponse(responseCode = "401", description = "GITHUB_TOKEN 미설정", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "fork 생성 또는 GitHub API 호출 실패", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    })
    public ResponseEntity<ContributeStartResponse> start(@Valid @RequestBody ContributeStartRequest request) {
        return ResponseEntity.ok(contributeService.start(request));
    }

    @PostMapping("/{repoOwner}/{repoName}/pr/prepare")
    @Operation(summary = "PR 메타데이터 준비", description = "Composer 1회 호출로 commit/PR 메타를 생성하고 DB에 캐시합니다.")
    public ResponseEntity<PrPrepareResponse> prepareForPr(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName
    ) {
        return ResponseEntity.ok(llmMetadataService.prepareForPr(repoOwner, repoName));
    }

    @PostMapping("/{repoOwner}/{repoName}/commit-push")
    @Operation(summary = "변경 사항 커밋 및 Push", description = "Contribute 모드 워크스페이스의 변경 사항을 커밋하고 fork origin의 feature branch로 push합니다. force push는 허용되지 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "커밋 및 push 성공"),
        @ApiResponse(responseCode = "404", description = "워크스페이스 없음", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "커밋할 변경 사항 없음", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "push 실패", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    })
    public ResponseEntity<CommitPushResponse> commitPush(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName,
        @Valid @RequestBody CommitPushRequest request
    ) {
        return ResponseEntity.ok(commitPushService.commitAndPush(repoOwner, repoName, request));
    }

    @PostMapping("/{repoOwner}/{repoName}/pull-request")
    @Operation(summary = "Pull Request 생성", description = "fork의 feature branch를 head로 사용하여 upstream 저장소에 draft PR을 생성합니다. 동일 head로 이미 PR이 있으면 details.existingPrUrl을 함께 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PR 생성 성공"),
        @ApiResponse(responseCode = "502", description = "PR 생성 실패 또는 중복 PR 존재", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    })
    public ResponseEntity<PullRequestResponse> createPullRequest(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName,
        @Valid @RequestBody PullRequestRequest request
    ) {
        return ResponseEntity.ok(pullRequestService.createPullRequest(repoOwner, repoName, request));
    }

    @GetMapping("/{repoOwner}/{repoName}/status")
    @Operation(summary = "Contribute 진행 상태 조회", description = "fork URL, fork 재사용 여부, 작업 브랜치, 마지막 커밋, PR URL 및 PR 상태를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상태 조회 성공"),
        @ApiResponse(responseCode = "400", description = "Contribute 모드 워크스페이스가 아님", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "워크스페이스 없음", content = @Content(schema = @Schema(implementation = com.demo.githubcopilotwithcursor.dto.ErrorResponse.class)))
    })
    public ResponseEntity<ContributeStatusResponse> status(
        @PathVariable("repoOwner") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = "^[A-Za-z0-9._-]+$") String repoName
    ) {
        return ResponseEntity.ok(contributeStatusService.status(repoOwner, repoName));
    }
}
