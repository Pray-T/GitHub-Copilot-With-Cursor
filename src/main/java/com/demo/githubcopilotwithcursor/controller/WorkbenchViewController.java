package com.demo.githubcopilotwithcursor.controller;

import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import com.demo.githubcopilotwithcursor.dto.CloneRequest;
import com.demo.githubcopilotwithcursor.dto.CloneResponse;
import com.demo.githubcopilotwithcursor.dto.CommitPushRequest;
import com.demo.githubcopilotwithcursor.dto.CommitPushResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.dto.PrPrepareResponse;
import com.demo.githubcopilotwithcursor.dto.PullRequestRequest;
import com.demo.githubcopilotwithcursor.dto.PullRequestResponse;
import com.demo.githubcopilotwithcursor.dto.WorkspaceListResponse;
import com.demo.githubcopilotwithcursor.dto.WorkspaceResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.cursor.CursorAuth;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubRepoRef;
import com.demo.githubcopilotwithcursor.service.AgentOrchestratorService;
import com.demo.githubcopilotwithcursor.service.CloneService;
import com.demo.githubcopilotwithcursor.service.CommitPushService;
import com.demo.githubcopilotwithcursor.service.DiffService;
import com.demo.githubcopilotwithcursor.service.DiffViewModelBuilder;
import com.demo.githubcopilotwithcursor.service.LlmMetadataService;
import com.demo.githubcopilotwithcursor.service.LaunchIdeService;
import com.demo.githubcopilotwithcursor.service.PullRequestService;
import com.demo.githubcopilotwithcursor.service.WorkspaceGuard;
import com.demo.githubcopilotwithcursor.service.WorkspaceService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/")
public class WorkbenchViewController {

    private static final Logger log = LoggerFactory.getLogger(WorkbenchViewController.class);
    private static final String REPO_SEGMENT_PATTERN = "^[A-Za-z0-9._-]+$";
    private static final String REPO_URL_FORMAT_ERROR = "GitHub 저장소 URL 형식이 올바르지 않습니다.";
    private static final String PR_PREPARE_REGENERATED_INFO =
        "로컬 IDE 추가 수정이 반영되어 PR 메타데이터를 새로 생성했습니다. "
            + "이전에 생성된 커밋 메시지, PR 제목, 본문과 다를 수 있습니다.";
    private static final String PR_PREPARE_FALLBACK_INFO =
        "Composer 호출에 실패해 기본 PR 메타데이터를 사용합니다.";

    private final CloneService cloneService;
    private final DiffService diffService;
    private final WorkspaceService workspaceService;
    private final DiffViewModelBuilder diffViewModelBuilder;
    private final CommitPushService commitPushService;
    private final PullRequestService pullRequestService;
    private final GitHubAuth gitHubAuth;
    private final CursorAuth cursorAuth;
    private final AgentOrchestratorService agentOrchestratorService;
    private final WebFormValidator webFormValidator;
    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceProperties workspaceProperties;
    private final CursorProperties cursorProperties;
    private final LaunchIdeService launchIdeService;
    private final LlmMetadataService llmMetadataService;

    public WorkbenchViewController(
        CloneService cloneService,
        DiffService diffService,
        WorkspaceService workspaceService,
        DiffViewModelBuilder diffViewModelBuilder,
        CommitPushService commitPushService,
        PullRequestService pullRequestService,
        GitHubAuth gitHubAuth,
        CursorAuth cursorAuth,
        AgentOrchestratorService agentOrchestratorService,
        WebFormValidator webFormValidator,
        WorkspaceGuard workspaceGuard,
        WorkspaceProperties workspaceProperties,
        CursorProperties cursorProperties,
        LaunchIdeService launchIdeService,
        LlmMetadataService llmMetadataService
    ) {
        this.cloneService = cloneService;
        this.diffService = diffService;
        this.workspaceService = workspaceService;
        this.diffViewModelBuilder = diffViewModelBuilder;
        this.commitPushService = commitPushService;
        this.pullRequestService = pullRequestService;
        this.gitHubAuth = gitHubAuth;
        this.cursorAuth = cursorAuth;
        this.agentOrchestratorService = agentOrchestratorService;
        this.webFormValidator = webFormValidator;
        this.workspaceGuard = workspaceGuard;
        this.workspaceProperties = workspaceProperties;
        this.cursorProperties = cursorProperties;
        this.launchIdeService = launchIdeService;
        this.llmMetadataService = llmMetadataService;
    }

    @GetMapping({"", "/", "/web"})
    public String index(Model model) {
        WorkspaceListResponse list = workspaceService.listWorkspaces();
        model.addAttribute("workspaces", list.items());
        boolean ready = gitHubAuth.isContributeEnabled() && cursorAuth.isCursorEnabled();
        model.addAttribute("contributeEnabled", ready);
        model.addAttribute("cursorEnabled", cursorAuth.isCursorEnabled());
        model.addAttribute("githubEnabled", gitHubAuth.isContributeEnabled());
        model.addAttribute("workspacePollIntervalMs", cursorProperties.getAgent().getPollIntervalMs());
        return "index";
    }

    @PostMapping("/web/clone")
    public String clone(
        @RequestParam("repoUrl") String repoUrl,
        @RequestParam("agentPrompt") String agentPrompt,
        @RequestParam(name = "mode", defaultValue = "REVIEW") String mode,
        RedirectAttributes redirectAttributes
    ) {
        WorkspaceMode workspaceMode = "CONTRIBUTE".equalsIgnoreCase(mode) ? WorkspaceMode.CONTRIBUTE : WorkspaceMode.REVIEW;
        CloneRequest cloneRequest = new CloneRequest(repoUrl, agentPrompt, workspaceMode);
        Optional<ConstraintViolation<CloneRequest>> validationViolation = webFormValidator.firstViolation(cloneRequest);
        if (validationViolation.isPresent()) {
            ConstraintViolation<CloneRequest> violation = validationViolation.get();
            if (isRepoUrlFormatIssue(violation, repoUrl)) {
                redirectAttributes.addFlashAttribute("errorMessage", REPO_URL_FORMAT_ERROR);
                redirectAttributes.addFlashAttribute("showRepoUrlHintPopup", true);
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", violation.getMessage());
            }
            redirectAttributes.addFlashAttribute("submittedRepoUrl", repoUrl);
            redirectAttributes.addFlashAttribute("submittedAgentPrompt", agentPrompt);
            return "redirect:/web";
        }

        try {
            CloneResponse response = cloneService.cloneRepository(cloneRequest);
            redirectAttributes.addFlashAttribute("clonedResponse", response);
            return workspaceRedirect(response.repoOwner(), response.repoName(), "/wait");
        } catch (AppException exception) {
            log.warn("Clone failed via web form: {}", exception.getMessage());
            if (exception.getErrorCode() == ErrorCode.WORKSPACE_ALREADY_EXISTS) {
                GitHubRepoRef conflict = extractConflictRepoRef(repoUrl);
                if (conflict != null) {
                    redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "워크스페이스 '" + conflict.owner() + "/" + conflict.repo() + "'가 이미 있습니다. 아래 목록에서 삭제한 뒤 다시 시도하세요."
                    );
                    redirectAttributes.addFlashAttribute("conflictRepoOwner", conflict.owner());
                    redirectAttributes.addFlashAttribute("conflictRepoName", conflict.repo());
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
                }
            } else if (exception.getErrorCode() == ErrorCode.INVALID_REPO_URL) {
                redirectAttributes.addFlashAttribute("errorMessage", REPO_URL_FORMAT_ERROR);
                redirectAttributes.addFlashAttribute("showRepoUrlHintPopup", true);
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            }
            redirectAttributes.addFlashAttribute("submittedRepoUrl", repoUrl);
            return "redirect:/web";
        }
    }

    private boolean isRepoUrlFormatIssue(ConstraintViolation<CloneRequest> violation, String repoUrl) {
        return "repoUrl".equals(violation.getPropertyPath().toString())
            && repoUrl != null
            && !repoUrl.isBlank();
    }

    @GetMapping("/web/workspaces/{repoOwner}/{repoName}/wait")
    public String wait(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        Model model
    ) {
        WorkspaceResponse workspace = workspaceService.findWorkspace(repoOwner, repoName);
        model.addAttribute("workspace", workspace);
        model.addAttribute("pollIntervalMs", cursorProperties.getAgent().getPollIntervalMs());
        return "wait";
    }

    @PostMapping("/web/workspaces/{repoOwner}/{repoName}/agent/cancel")
    public String cancelAgent(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        RedirectAttributes redirectAttributes
    ) {
        try {
            agentOrchestratorService.cancel(repoOwner, repoName);
            redirectAttributes.addFlashAttribute("infoMessage", "Cursor Cloud Agent 실행을 취소했습니다.");
        } catch (AppException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/web";
    }

    @GetMapping("/web/workspaces/{repoOwner}/{repoName}/diff")
    public String diff(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        Model model
    ) {
        DiffResponse diffResponse = diffService.diff(repoOwner, repoName, true, 0);
        DiffViewModelBuilder.DiffViewModel viewModel = diffViewModelBuilder.build(diffResponse);
        WorkspaceResponse workspace = workspaceService.findWorkspace(repoOwner, repoName);
        model.addAttribute("diff", viewModel);
        model.addAttribute("workspace", workspace);
        boolean reviewMode = "REVIEW".equalsIgnoreCase(workspace.mode());
        model.addAttribute("reviewMode", reviewMode);
        if (!reviewMode) {
            RepositoryWorkspace entity = workspaceService.requireContributeWorkspace(repoOwner, repoName);
            model.addAttribute("llmMetadataStale", llmMetadataService.isCachedMetadataStale(entity, diffResponse));
        } else {
            model.addAttribute("llmMetadataStale", false);
        }
        return "diff";
    }

    @PostMapping("/web/workspaces/{repoOwner}/{repoName}/launch-ide")
    public String launchIde(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        RedirectAttributes redirectAttributes
    ) {
        try {
            launchIdeService.launchIde(repoOwner, repoName);
            redirectAttributes.addFlashAttribute(
                "infoMessage",
                "Cursor IDE 실행을 요청했습니다. IDE에서 수정한 뒤 대기 화면의 「변경 확인」으로 돌아오세요."
            );
        } catch (AppException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return workspaceRedirect(repoOwner, repoName, "/diff");
        }
        return workspaceRedirect(repoOwner, repoName, "/wait");
    }

    @PostMapping("/web/workspaces/{repoOwner}/{repoName}/pr/prepare")
    public String prepareForPr(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        RedirectAttributes redirectAttributes
    ) {
        try {
            PrPrepareResponse response = llmMetadataService.prepareForPr(repoOwner, repoName);
            applyPrPrepareInfoFlash(response, redirectAttributes);
            String suffix = PrPrepareResponse.NEXT_COMMIT_FORM.equals(response.nextStep()) ? "/commit" : "/pr";
            return workspaceRedirect(repoOwner, repoName, suffix);
        } catch (AppException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return workspaceRedirect(repoOwner, repoName, "/diff");
        }
    }

    private void applyPrPrepareInfoFlash(PrPrepareResponse response, RedirectAttributes redirectAttributes) {
        if (response.metadataRegeneratedDueToDiffChange()) {
            redirectAttributes.addFlashAttribute("infoMessage", PR_PREPARE_REGENERATED_INFO);
            return;
        }
        if (response.fallbackUsed()) {
            redirectAttributes.addFlashAttribute("infoMessage", PR_PREPARE_FALLBACK_INFO);
        }
    }

    @GetMapping("/web/workspaces/{repoOwner}/{repoName}/commit")
    public String commit(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        WorkspaceResponse workspace = workspaceService.findWorkspace(repoOwner, repoName);
        if (workspace.branchName() == null || workspace.branchName().isBlank()) {
            throw new AppException(
                ErrorCode.NOT_CONTRIBUTE_WORKSPACE,
                WorkspaceService.NOT_CONTRIBUTE_WORKSPACE_MESSAGE
            );
        }
        if (workspace.llmCachedAt() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "먼저 Diff 화면에서 「PR 진행」을 클릭해 주세요.");
            return workspaceRedirect(repoOwner, repoName, "/diff");
        }
        model.addAttribute("workspace", workspace);
        model.addAttribute("authorName", gitHubAuth.defaultAuthorName());
        model.addAttribute("authorEmail", gitHubAuth.defaultAuthorEmail());
        String defaultMessage = workspace.llmCommitMessage() != null ? workspace.llmCommitMessage() : "";
        model.addAttribute("defaultCommitMessage", defaultMessage);
        return "commit";
    }

    @PostMapping("/web/workspaces/{repoOwner}/{repoName}/commit-push")
    public String commitPush(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        @Valid @ModelAttribute("commitPushForm") CommitPushRequest commitPushForm,
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes
    ) {
        Optional<String> validationError = webFormValidator.firstBindingError(bindingResult);
        if (validationError.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", validationError.get());
            redirectAttributes.addFlashAttribute("submittedMessage", commitPushForm.message());
            redirectAttributes.addFlashAttribute("authorName", commitPushForm.authorName());
            redirectAttributes.addFlashAttribute("authorEmail", commitPushForm.authorEmail());
            return workspaceRedirect(repoOwner, repoName, "/commit");
        }

        try {
            CommitPushResponse response = commitPushService.commitAndPush(repoOwner, repoName, commitPushForm);
            redirectAttributes.addFlashAttribute("infoMessage", "커밋 및 push 완료: " + response.commitSha());
            return workspaceRedirect(repoOwner, repoName, "/pr");
        } catch (AppException exception) {
            log.warn("Commit/push failed via web form: {}", exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("submittedMessage", commitPushForm.message());
            redirectAttributes.addFlashAttribute("authorName", commitPushForm.authorName());
            redirectAttributes.addFlashAttribute("authorEmail", commitPushForm.authorEmail());
            return workspaceRedirect(repoOwner, repoName, "/commit");
        }
    }

    @GetMapping("/web/workspaces/{repoOwner}/{repoName}/pr")
    public String pr(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        WorkspaceResponse workspace = workspaceService.findWorkspace(repoOwner, repoName);
        if (workspace.branchName() == null || workspace.branchName().isBlank()) {
            throw new AppException(
                ErrorCode.NOT_CONTRIBUTE_WORKSPACE,
                WorkspaceService.NOT_CONTRIBUTE_WORKSPACE_MESSAGE
            );
        }
        if (workspace.llmCachedAt() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "먼저 Diff 화면에서 「PR 진행」을 클릭해 주세요.");
            return workspaceRedirect(repoOwner, repoName, "/diff");
        }
        model.addAttribute("workspace", workspace);
        if (workspace.prUrl() == null || workspace.prUrl().isBlank()) {
            PullRequestService.PullRequestDraft draft = pullRequestService.draft(repoOwner, repoName);
            String title = workspace.llmPrTitle() != null ? workspace.llmPrTitle() : draft.title();
            String body = workspace.llmPrBody() != null ? workspace.llmPrBody() : draft.body();
            model.addAttribute("prDraft", new PullRequestService.PullRequestDraft(title, body, draft.base(), draft.draft()));
        }
        return "pr";
    }

    @PostMapping("/web/workspaces/{repoOwner}/{repoName}/create-pr")
    public String createPr(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        @RequestParam("title") String title,
        @RequestParam("body") String body,
        @RequestParam(name = "base", required = false) String base,
        @RequestParam(name = "draft", required = false) String draftParam,
        RedirectAttributes redirectAttributes
    ) {
        boolean draft = "true".equalsIgnoreCase(draftParam);
        PullRequestRequest pullRequestForm = new PullRequestRequest(title, body, base, draft);
        Optional<String> validationError = webFormValidator.firstMessage(pullRequestForm);
        if (validationError.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", validationError.get());
            redirectAttributes.addFlashAttribute("submittedTitle", title);
            redirectAttributes.addFlashAttribute("submittedBody", body);
            redirectAttributes.addFlashAttribute("submittedBase", base);
            redirectAttributes.addFlashAttribute("submittedDraft", draft);
            return workspaceRedirect(repoOwner, repoName, "/pr");
        }

        try {
            PullRequestResponse response = pullRequestService.createPullRequest(repoOwner, repoName, pullRequestForm);
            redirectAttributes.addFlashAttribute("infoMessage", "PR 생성 완료: " + response.prUrl());
            return workspaceRedirect(repoOwner, repoName, "/pr");
        } catch (AppException exception) {
            log.warn("Pull request creation failed via web form: {}", exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            String existingPrUrl = existingPrUrl(exception);
            if (existingPrUrl != null) {
                redirectAttributes.addFlashAttribute("existingPrUrl", existingPrUrl);
            }
            redirectAttributes.addFlashAttribute("submittedTitle", title);
            redirectAttributes.addFlashAttribute("submittedBody", body);
            redirectAttributes.addFlashAttribute("submittedBase", base);
            redirectAttributes.addFlashAttribute("submittedDraft", draft);
            return workspaceRedirect(repoOwner, repoName, "/pr");
        }
    }

    @PostMapping("/web/workspaces/{repoOwner}/{repoName}/delete")
    public String delete(
        @PathVariable("repoOwner") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoOwner,
        @PathVariable("repoName") @Pattern(regexp = REPO_SEGMENT_PATTERN) String repoName,
        RedirectAttributes redirectAttributes
    ) {
        try {
            workspaceService.deleteWorkspace(repoOwner, repoName);
            redirectAttributes.addFlashAttribute(
                "infoMessage",
                WorkspaceService.DELETE_SUCCESS_WITH_IDE_HINT
            );
        } catch (AppException exception) {
            log.warn("Delete failed via web form: {}", exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/web";
    }

    @ExceptionHandler(AppException.class)
    public String handleAppException(AppException exception, RedirectAttributes redirectAttributes) {
        log.warn("View controller error: {}", exception.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        return "redirect:/web";
    }

    private String workspaceRedirect(String repoOwner, String repoName, String suffix) {
        return "redirect:/web/workspaces/" + repoOwner + "/" + repoName + suffix;
    }

    private GitHubRepoRef extractConflictRepoRef(String repoUrl) {
        try {
            URI uri = workspaceGuard.parseRepoUrl(repoUrl, workspaceProperties.getAllowedHosts());
            return workspaceGuard.extractRepoRef(uri);
        } catch (AppException ignored) {
            return null;
        }
    }

    private String existingPrUrl(AppException exception) {
        Map<String, Object> details = exception.getDetails();
        if (details == null) {
            return null;
        }
        Object value = details.get("existingPrUrl");
        return value instanceof String url && !url.isBlank() ? url : null;
    }
}
