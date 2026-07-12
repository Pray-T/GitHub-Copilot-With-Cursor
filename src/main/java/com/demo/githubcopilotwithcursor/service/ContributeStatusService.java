package com.demo.githubcopilotwithcursor.service;

import com.demo.githubcopilotwithcursor.domain.AgentStatus;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.dto.ContributeStatusResponse;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.github.GitHubApiClient;
import com.demo.githubcopilotwithcursor.github.GitHubPullRequest;
import com.demo.githubcopilotwithcursor.github.GitHubPullRequestRef;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContributeStatusService {

    private final WorkspaceGuard workspaceGuard;
    private final WorkspaceService workspaceService;
    private final GitHubApiClient gitHubApiClient;

    public ContributeStatusService(
        WorkspaceGuard workspaceGuard,
        WorkspaceService workspaceService,
        GitHubApiClient gitHubApiClient
    ) {
        this.workspaceGuard = workspaceGuard;
        this.workspaceService = workspaceService;
        this.gitHubApiClient = gitHubApiClient;
    }

    @Transactional(readOnly = true)
    public ContributeStatusResponse status(String repoOwner, String repoName) {
        RepositoryWorkspace workspace = workspaceService.requireContributeWorkspace(repoOwner, repoName);

        return new ContributeStatusResponse(
            workspace.getRepoOwner(),
            workspace.getRepoName(),
            workspace.getMode() != null ? workspace.getMode().name() : WorkspaceMode.CONTRIBUTE.name(),
            new ContributeStatusResponse.ForkInfo(workspace.getForkUrl(), workspace.isForkReused()),
            workspace.getBranchName(),
            toAgentSummary(workspace),
            readLastCommit(workspace),
            workspace.getPrUrl(),
            resolvePrState(workspace.getPrUrl()),
            toLlmCacheSummary(workspace)
        );
    }

    private ContributeStatusResponse.AgentSummary toAgentSummary(RepositoryWorkspace workspace) {
        AgentStatus status = workspace.getAgentStatus();
        return new ContributeStatusResponse.AgentSummary(
            workspace.getCursorAgentId(),
            status != null ? status.name() : null,
            workspace.getAgentStartedAt(),
            workspace.getAgentCompletedAt()
        );
    }

    private ContributeStatusResponse.LlmCacheSummary toLlmCacheSummary(RepositoryWorkspace workspace) {
        return new ContributeStatusResponse.LlmCacheSummary(
            workspace.getLlmCachedAt(),
            hasText(workspace.getLlmCommitMessage()),
            hasText(workspace.getLlmPrTitle()),
            hasText(workspace.getLlmPrBody())
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolvePrState(String prUrl) {
        if (prUrl == null || prUrl.isBlank()) {
            return null;
        }
        try {
            GitHubPullRequestRef ref = GitHubPullRequestRef.fromUrl(prUrl);
            GitHubPullRequest pullRequest = gitHubApiClient.getPullRequest(ref.owner(), ref.repo(), ref.number());
            if (Boolean.TRUE.equals(pullRequest.merged())) {
                return "merged";
            }
            return pullRequest.state();
        } catch (AppException exception) {
            return "unknown";
        }
    }

    private ContributeStatusResponse.LastCommit readLastCommit(RepositoryWorkspace workspace) {
        Path workspacePath = workspaceGuard.normalizeStoredPath(workspace.getWorkspacePath());
        try (Git git = Git.open(workspacePath.toFile())) {
            Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
            Iterator<RevCommit> iterator = commits.iterator();
            RevCommit commit = iterator.hasNext() ? iterator.next() : null;
            if (commit == null) {
                return null;
            }
            OffsetDateTime committedAt = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(commit.getCommitTime()),
                ZoneOffset.UTC
            );
            return new ContributeStatusResponse.LastCommit(commit.getId().name(), commit.getShortMessage(), committedAt);
        } catch (Exception exception) {
            throw workspaceService.buildGitAccessFailure(
                workspace.getRepoOwner(),
                workspace.getRepoName(),
                workspacePath,
                exception
            );
        }
    }
}
