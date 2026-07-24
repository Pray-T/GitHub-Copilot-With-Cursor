package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.dto.ChangedFileResponse;
import com.demo.githubcopilotwithcursor.dto.DiffResponse;
import com.demo.githubcopilotwithcursor.dto.PullRequestRequest;
import com.demo.githubcopilotwithcursor.dto.PullRequestResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.exception.ErrorCode;
import com.demo.githubcopilotwithcursor.github.CreatePullRequestPayload;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubPullRequest;
import com.demo.githubcopilotwithcursor.github.GitHubRepoRef;
import com.demo.githubcopilotwithcursor.github.GitHubRepository;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PullRequestService {

    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final RepositoryWorkspaceRepository repository;
    private final GitHubAuth gitHubAuth;
    private final GitHubApiClient gitHubApiClient;
    private final DiffService diffService;
    private final PushService pushService;
    private final WorkspaceGitStateService workspaceGitStateService;

    public PullRequestService(
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        RepositoryWorkspaceRepository repository,
        GitHubAuth gitHubAuth,
        GitHubApiClient gitHubApiClient,
        DiffService diffService,
        PushService pushService,
        WorkspaceGitStateService workspaceGitStateService
    ) {
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.repository = repository;
        this.gitHubAuth = gitHubAuth;
        this.gitHubApiClient = gitHubApiClient;
        this.diffService = diffService;
        this.pushService = pushService;
        this.workspaceGitStateService = workspaceGitStateService;
    }

    @Transactional
    public PullRequestResponse createPullRequest(String repoOwner, String repoName, PullRequestRequest request) {
        String login = gitHubAuth.githubLogin();
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        RepositoryWorkspace workspace = workspaceService.requireContributeWorkspace(repoOwner, repoName);
        ensureBranchPushed(repoOwner, repoName, workspace);
        GitHubRepoRef upstream = GitHubRepoRef.fromUrl(workspace.getUpstreamUrl());
        String base = normalizeBase(request.base(), upstream);
        boolean draft = request.draft() == null || request.draft();
        String head = login + ":" + workspace.getBranchName();

        GitHubPullRequest pullRequest = gitHubApiClient.createPullRequest(
            upstream.owner(),
            upstream.repo(),
            new CreatePullRequestPayload(request.title(), request.body(), base, head, draft)
        );
        workspace.attachPullRequest(pullRequest.htmlUrl());
        repository.save(workspace);

        return new PullRequestResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            pullRequest.htmlUrl(),
            pullRequest.number(),
            pullRequest.state(),
            pullRequest.draft(),
            base,
            head,
            OffsetDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public PullRequestDraft draft(String repoOwner, String repoName) {
        workspaceGuard.validateRepoOwner(repoOwner);
        workspaceGuard.validateRepoName(repoName);
        RepositoryWorkspace workspace = workspaceService.requireContributeWorkspace(repoOwner, repoName);
        GitHubRepoRef upstream = GitHubRepoRef.fromUrl(workspace.getUpstreamUrl());
        String base = normalizeBase(null, upstream);
        String title = "Update " + workspace.getRepoName();
        String body = buildDefaultBody(repoOwner, repoName);
        return new PullRequestDraft(title, body, base, true);
    }

    public String buildDefaultBody(String repoOwner, String repoName) {
        DiffResponse diff = diffService.diffWithoutPersist(repoOwner, repoName, false, 1);
        List<ChangedFileResponse> files = diff.changedFiles();
        if (files.isEmpty()) {
            return "## 변경 파일\n\n- 변경 파일 없음\n";
        }
        StringBuilder builder = new StringBuilder("## 변경 파일\n\n");
        for (ChangedFileResponse file : files) {
            builder
                .append("- ")
                .append(shortChangeType(file.changeType()))
                .append(' ')
                .append(file.path());
            if (file.oldPath() != null) {
                builder.append(" (from ").append(file.oldPath()).append(')');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private void ensureBranchPushed(String repoOwner, String repoName, RepositoryWorkspace workspace) {
        if (!workspaceGitStateService.hasUnpushedCommits(workspace)) {
            return;
        }
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        pushService.push(repoOwner, repoName, workspacePath, workspace.getBranchName());
    }

    private String normalizeBase(String requestedBase, GitHubRepoRef upstream) {
        if (requestedBase != null && !requestedBase.isBlank()) {
            return requestedBase.trim();
        }
        GitHubRepository upstreamRepository = gitHubApiClient.getRepository(upstream.owner(), upstream.repo());
        if (upstreamRepository.defaultBranch() == null || upstreamRepository.defaultBranch().isBlank()) {
            throw new AppException(ErrorCode.GITHUB_API_ERROR, "GitHub 저장소의 기본 브랜치를 확인하지 못했습니다.");
        }
        return upstreamRepository.defaultBranch();
    }

    private String shortChangeType(String changeType) {
        return switch (changeType) {
            case "ADDED" -> "A";
            case "DELETED" -> "D";
            case "RENAMED" -> "R";
            default -> "M";
        };
    }

    public record PullRequestDraft(String title, String body, String base, boolean draft) {
    }
}
